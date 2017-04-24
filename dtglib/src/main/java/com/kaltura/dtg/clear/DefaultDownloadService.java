package com.kaltura.dtg.clear;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.kaltura.android.exoplayer.hls.Variant;
import com.kaltura.dtg.DownloadItem;
import com.kaltura.dtg.DownloadState;
import com.kaltura.dtg.DownloadStateListener;
import com.kaltura.dtg.Utils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

public class DefaultDownloadService extends Service {

    static final String TAG = "DefaultDownloadService";
    private LocalBinder localBinder = new LocalBinder();
    private int maxConcurrentDownloads = 1;
    private Database database;
    private File downloadsDir;
    private boolean started;
    private DownloadStateListener downloadStateListener;
    private ExecutorService mExecutor = Executors.newFixedThreadPool(maxConcurrentDownloads);
    private ItemFutureMap futureMap = new ItemFutureMap();
    private Handler listenerHandler = null;
    private final DownloadTask.Listener mDownloadTaskListener = new DownloadTask.Listener() {

        @Override
        public void onTaskProgress(DownloadTask task, DownloadTask.State newState, int newBytes) {
            String itemId = task.itemId;
            final DefaultDownloadItem item = findItemImpl(itemId);
            if (item == null) {
                Log.e(TAG, "Can't find item by id: " + itemId + "; taskId: " + task.taskId);
                return;
            }
            
            int pendingCount = -1;
            if (newState == DownloadTask.State.COMPLETED) {
                database.markTaskAsComplete(task);
                pendingCount = countPendingFiles(itemId, null);
                Log.i(TAG, "Pending tasks for item: " + pendingCount);
            }

            if (newState == DownloadTask.State.ERROR) {
                item.setBroken(true);
            }

            final long totalBytes = item.incDownloadBytes(newBytes);
            updateItemInfoInDB(item, Database.COL_ITEM_DOWNLOADED_SIZE);

            if (pendingCount == 0) {
                // We finished the last (or only) chunk of the item.
                database.setDownloadFinishTime(itemId);

                // TODO: revisit this "broken" notion.
                if (item.isBroken()) {
                    item.setState(DownloadState.PAUSED);
                    database.updateItemState(item.getItemId(), DownloadState.PAUSED);
                    listenerHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            downloadStateListener.onDownloadStop(item);
                        }
                    });
                } else {
                    item.setState(DownloadState.COMPLETED);
                    database.updateItemState(item.getItemId(), DownloadState.COMPLETED);
                    listenerHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            downloadStateListener.onDownloadComplete(item);
                        }
                    });
                }
            } else {
                listenerHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        downloadStateListener.onProgressChange(item, totalBytes);
                    }
                });
            }
        }
    };
    private HandlerThread listenerThread = null;

    @Override
    public void onCreate() {
        Log.d(TAG, "*** onCreate");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "*** onBind");
        start();
        return localBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        stop();
        return super.onUnbind(intent);
    }

    public void setMaxConcurrentDownloads(int maxConcurrentDownloads) {
        this.maxConcurrentDownloads = maxConcurrentDownloads;
    }

    public void startListenerThread(){
        listenerThread = new HandlerThread("DownloadStateListener");
        listenerThread.start();
        listenerHandler = new Handler(listenerThread.getLooper());
    }

    void pauseItemDownload(String itemId) {
        if (itemId != null) {
            futureMap.cancelItem(itemId);
        } else {
            futureMap.cancelAll();
        }
        // Maybe add PAUSE_ALL with mExecutor.purge(); and remove futures
    }

    void downloadChunks(ArrayList<DownloadTask> chunks, String itemId) {
        if (chunks == null) {
            return;
        }
        for (DownloadTask task : chunks) {
            task.itemId = itemId;
            FutureTask future = futureTask(itemId, task);
            mExecutor.execute(future);
            futureMap.add(itemId, future);
        }
    }

    void updateItemInfoInDB(DefaultDownloadItem item, String... columns) {
        database.updateItemInfo(item, columns);
    }

    private void assertStarted() {
        if (!started) {
            throw new IllegalStateException("Service not started");
        }
    }
    
    public void start() {

        Log.d(TAG, "start()");

        File dataDir = new File(getFilesDir(), "dtg/clear");
        dataDir.mkdirs();
        if (!dataDir.isDirectory()) {
            Log.e(TAG, "Failed to create provider data directory " + dataDir);
            throw new IllegalStateException("Can't continue without the data directory, " + dataDir);
        }

        File extFilesDir = getExternalFilesDir(null);
        if (extFilesDir != null) {
            downloadsDir = new File(extFilesDir, "dtg/clear");
            downloadsDir.mkdirs();
            if (!downloadsDir.isDirectory()) {
                Log.e(TAG, "Failed to create provider downloads directory " + downloadsDir);
                throw new IllegalStateException("Can't continue without the downloads directory, " + downloadsDir);
            }

        } else {
            downloadsDir = dataDir;
        }

        File dbFile = new File(dataDir, "downloads.db");

        database = new Database(dbFile, this);

        startListenerThread();
        
        started = true;
    }

    public void stop() {
        
        Log.d(TAG, "stop()");
        
        if (started) {

            // close db
            database.close();
            database = null;
            
            started = false;
        }
    }

    public void loadItemMetadata(final DefaultDownloadItem item) {
        assertStarted();

        new Thread() {
            @Override
            public void run() {
                try {
                    downloadMetadata(item);
                    item.setState(DownloadState.INFO_LOADED);
                    updateItemInfoInDB(item,
                            Database.COL_ITEM_STATE, Database.COL_ITEM_ESTIMATED_SIZE,
                            Database.COL_ITEM_PLAYBACK_PATH);
                    downloadStateListener.onDownloadMetadata(item, null);

                } catch (IOException e) {
                    Log.e(TAG, "Failed to download metadata for " + item.getItemId(), e);
                    downloadStateListener.onDownloadMetadata(item, e);
                }
            }
        }.start();
    }

    public File getItemDataDir(String itemId) {
        assertStarted();

        return new File(downloadsDir, "items/" + itemId + "/data");    // TODO: make sure name is safe.
    }

    private void downloadMetadata(DefaultDownloadItem item) throws IOException {

        File itemDataDir = getItemDataDir(item.getItemId());
        String contentURL = item.getContentURL();
        if (contentURL.startsWith("widevine")) {
            contentURL = contentURL.replaceFirst("widevine", "http");
        }
        
        URL url = new URL(contentURL);
        String path = url.getPath().toLowerCase();
        if (path.endsWith(".m3u8")) {
            downloadMetadataHLS(item, itemDataDir);
        } else if (path.endsWith(".mpd")) {
            downloadMetadataDash(item, itemDataDir);
        } else {
            downloadMetadataSimple(url, item, itemDataDir);
        }
    }

    private void downloadMetadataDash(DefaultDownloadItem item, File itemDataDir) throws IOException {

        final DashDownloader dashDownloader = new DashDownloadCreator(item.getContentURL(), itemDataDir);

        DownloadItem.TrackSelector trackSelector = dashDownloader.getTrackSelector();
        
        item.setTrackSelector(trackSelector);
        
        downloadStateListener.onTracksAvailable(item, trackSelector);
        
        dashDownloader.apply();

        item.setTrackSelector(null);


        List<DashTrack> availableTracks = dashDownloader.getAvailableTracks();
        List<DashTrack> selectedTracks = dashDownloader.getSelectedTracks();
        
        database.addTracks(item, availableTracks, selectedTracks);

        long estimatedDownloadSize = dashDownloader.getEstimatedDownloadSize();
        item.setEstimatedSizeBytes(estimatedDownloadSize);

        LinkedHashSet<DownloadTask> downloadTasks = dashDownloader.getDownloadTasks();
        Log.d(TAG, "tasks:" + downloadTasks);

        item.setPlaybackPath(dashDownloader.getPlaybackPath());

        addDownloadTasksToDB(item, new ArrayList<>(downloadTasks));
    }

    private void downloadMetadataSimple(URL url, DefaultDownloadItem item, File itemDataDir) throws IOException {

        long length = Utils.httpHeadGetLength(url);

        File targetFile = new File(itemDataDir, url.getPath());
        DownloadTask downloadTask = new DownloadTask(url, targetFile);

        item.setEstimatedSizeBytes(length);
        item.setPlaybackPath(url.getPath().substring(1));

        ArrayList<DownloadTask> downloadTasks = new ArrayList<>(1);
        downloadTasks.add(downloadTask);

        addDownloadTasksToDB(item, downloadTasks);
    }

    void addDownloadTasksToDB(DefaultDownloadItem item, List<DownloadTask> tasks) {
        
        // Filter-out things that are already 
        
        database.addDownloadTasksToDB(item, tasks);
    }

    private void downloadMetadataHLS(DefaultDownloadItem item, File itemDataDir) throws IOException {
        HLSParser hlsParser = new HLSParser(item, itemDataDir);


        hlsParser.parseMaster();

        // Select best bitrate
        TreeSet<Variant> sortedVariants = hlsParser.getSortedVariants();
        Variant selectedVariant = sortedVariants.first();   // first == highest bitrate
        hlsParser.selectVariant(selectedVariant);

        hlsParser.parseVariant();
        ArrayList<DownloadTask> chunks = hlsParser.createDownloadTasks();
        item.setEstimatedSizeBytes(hlsParser.getEstimatedSizeBytes());

        // set playback path the the relative url path, excluding the leading slash.
        item.setPlaybackPath(hlsParser.getPlaybackPath());

        addDownloadTasksToDB(item, chunks);
        // TODO: handle db insertion errors

    }

    public DownloadState startDownload(final String itemId) {
        assertStarted();
        
        final DefaultDownloadItem item = findItemImpl(itemId);

        item.setState(DownloadState.IN_PROGRESS);
        
        listenerHandler.post(new Runnable() {
            @Override
            public void run() {
                downloadStateListener.onDownloadStart(item);
            }
        });
        
        // Read download tasks from db
        ArrayList<DownloadTask> chunksToDownload = database.readPendingDownloadTasksFromDB(itemId);

        if (chunksToDownload.isEmpty()) {
            database.updateItemState(itemId, DownloadState.COMPLETED);
            
            listenerHandler.post(new Runnable() {
                @Override
                public void run() {
                    downloadStateListener.onDownloadComplete(item);
                }
            });
            
        } else {
            downloadChunks(chunksToDownload, itemId);
            database.updateItemState(itemId, DownloadState.IN_PROGRESS);
        }
        
        return item.getState();
    }

    public void pauseDownload(final DefaultDownloadItem item) {
        assertStarted();

        ArrayList<DownloadTask> downloadTasks;
        if (item != null) {
            downloadTasks = database.readPendingDownloadTasksFromDB(item.getItemId());
            if (downloadTasks.size() > 0) {

                pauseItemDownload(item.getItemId());

                item.setState(DownloadState.PAUSED);
                database.updateItemState(item.getItemId(), DownloadState.PAUSED);
                
                listenerHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        downloadStateListener.onDownloadPause(item);
                    }
                });
            }
        }
    }

    public void resumeDownload(DefaultDownloadItem item) {
        assertStarted();

        // resume should be considered as download start

        DownloadState itemState = startDownload(item.getItemId());
        item.updateItemState(itemState);
    }

    public void removeItem(DefaultDownloadItem item) {
        assertStarted();

        if (item == null) {
            return;
        }
        pauseDownload(item);
        deleteItemFiles(item.getItemId());
        database.removeItemFromDB(item);
    }

    private void deleteItemFiles(String item) {
        String path = downloadsDir + "/items/" + item;
        File file = new File(path);

        Utils.deleteRecursive(file);
    }

    private DefaultDownloadItem findItemImpl(String itemId) {

        // TODO: cache items in memory?
        
        DefaultDownloadItem item = database.findItemInDB(itemId);
        if (item != null) {
            item.setProvider(this);
        }
                
        return item;
    }

    /**
     * Find and return an item.
     *
     * @return An item identified by itemId, or null if not found.
     */
    
    public DefaultDownloadItem findItem(String itemId) {
        assertStarted();

        return findItemImpl(itemId);
    }

    public long getDownloadedItemSize(@Nullable String itemId) {
        return database.getDownloadedItemSize(itemId);
    }

    public DefaultDownloadItem createItem(String itemId, String contentURL) {
        assertStarted();

        DefaultDownloadItem item = findItemImpl(itemId);
        // If item already exists, return null.
        if (item != null) {
            return null;    // don't create, DON'T return db item.
        }

        if (contentURL == null) {
            Log.e(TAG, "Can't add item with contentURL==null");
            return null;
        }

        item = new DefaultDownloadItem(itemId, contentURL);
        item.setState(DownloadState.NEW);
        item.setAddedTime(System.currentTimeMillis());
        File itemDataDir = getItemDataDir(itemId);
        itemDataDir.mkdirs();
        if (!itemDataDir.isDirectory()) {
            Log.e(TAG, "Failed to create item data directory " + itemDataDir);
            throw new IllegalStateException("Can't continue without item data directory, " + itemDataDir);
        }

        item.setDataDir(itemDataDir.getAbsolutePath());

        database.addItemToDB(item, itemDataDir);

        item.setProvider(this);
        return item;
    }

    public void updateItemState(String itemId, DownloadState state) {
        assertStarted();

        database.updateItemState(itemId, state);
    }

    public List<DefaultDownloadItem> getDownloads(DownloadState[] states) {
        assertStarted();

        ArrayList<DefaultDownloadItem> items = database.readItemsFromDB(states);

        for (DefaultDownloadItem item : items) {
            item.setProvider(this);
        }

        return Collections.unmodifiableList(items);
    }

    public String getPlaybackURL(String itemId) {
        File localFile = getLocalFile(itemId);
        if (localFile == null) {
            return null;
        }
        return Uri.fromFile(localFile).toString();
    }
    
    public File getLocalFile(String itemId) {
        assertStarted();

        DefaultDownloadItem item = findItemImpl(itemId);
        if (item == null) {
            return null;
        }

        String playbackPath = item.getPlaybackPath();
        if (playbackPath == null) {
            return null;
        }

        return new File(item.getDataDir(), playbackPath);
    }
    
    public void dumpState() {
    }
    
    public void setDownloadStateListener(DownloadStateListener listener) {
        if (listener == null) {
            listener = DownloadStateListener.noopListener;
        }
        downloadStateListener = listener;
    }

    public long getEstimatedItemSize(String itemId) {
        assertStarted();

        return database.getEstimatedItemSize(itemId);
    }

    List<DashTrack> readTracksFromDB(String itemId, DownloadItem.TrackType trackType, DashDownloader.TrackState state) {
        return database.readTracks(itemId, trackType, state);
    }
    
    void updateTracksInDB(String itemId, Map<DownloadItem.TrackType, List<DashTrack>> tracksMap, DashDownloader.TrackState state) {
        database.updateTracksState(itemId, DashDownloader.flattenTrackList(tracksMap), state);
    }

    int countPendingFiles(String itemId, @Nullable String trackId) {
        return database.countPendingFiles(itemId, trackId);
    }

    private FutureTask futureTask(final String itemId, final DownloadTask task) {
        task.setListener(mDownloadTaskListener);
        Callable<Void> callable = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                task.download();
                return null;
            }
        };
        return new FutureTask<Void>(callable) {
            @Override
            protected void done() {
                futureMap.remove(itemId, this);
            }
        };
    }

    class LocalBinder extends Binder {
        DefaultDownloadService getService() {
            return DefaultDownloadService.this;
        }
    }
}
