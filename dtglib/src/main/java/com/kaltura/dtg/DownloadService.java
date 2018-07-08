package com.kaltura.dtg;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.kaltura.dtg.DownloadItem.TrackType;
import com.kaltura.dtg.dash.DashDownloader;
import com.kaltura.dtg.hls.HlsDownloader;

import java.io.File;
import java.io.IOException;
import java.net.HttpRetryException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public class DownloadService extends Service {

    private static final String TAG = "DownloadService";
    private final Context context;  // allow mocking
    private LocalBinder localBinder = new LocalBinder();
    private Database database;
    private DownloadRequestParams.Adapter adapter;
    private File downloadsDir;
    private boolean started;
    private boolean stopping;
    private DownloadStateListener downloadStateListener;
    private ExecutorService executorService;
    private ItemFutureMap futureMap = new ItemFutureMap();
    private Handler listenerHandler = null;
    
    private Handler taskProgressHandler = null;
    private ContentManager.Settings settings;
    
    private Set<String> removedItems = new HashSet<>();
    private String NO_MEDIA_EMPTY_FILE = ".nomedia";

    public DownloadService(Context context) {
        this.context = context;
    }
    
    public DownloadService() {
        this.context = this;
    }
    
    private final DownloadTask.Listener mDownloadTaskListener = new DownloadTask.Listener() {

        @Override
        public void onTaskProgress(final DownloadTask task, final DownloadTask.State newState, final int newBytes, final Exception stopError) {
            if (taskProgressHandler.getLooper().getThread().isAlive()) {
                taskProgressHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        DownloadService.this.onTaskProgress(task, newState, newBytes, stopError);
                    }
                });
            }
        }
    };

    private void onTaskProgress(DownloadTask task, DownloadTask.State newState, int newBytes, final Exception stopError) {

        if (stopping) {
            return;
        }
        
        String itemId = task.itemId;
        
        if (removedItems.contains(itemId)) {
            // Ignore this report.
            return;
        }
        
        final DownloadItemImp item = findItemImpl(itemId);
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
            Log.d(TAG, "Task has failed; cancelling item " + itemId);
            item.setState(DownloadState.FAILED);
            database.updateItemState(itemId, DownloadState.FAILED);
            futureMap.cancelItem(itemId);
            listenerHandler.post(new Runnable() {
                @Override
                public void run() {
                    downloadStateListener.onDownloadFailure(item, stopError);
                }
            });
            return;
        }
        
        final long totalBytes = item.incDownloadBytes(newBytes);
        updateItemInfoInDB(item, Database.COL_ITEM_DOWNLOADED_SIZE);

        if (pendingCount == 0) {
            // We finished the last (or only) chunk of the item.
            database.setDownloadFinishTime(itemId);

            item.setState(DownloadState.COMPLETED);
            database.updateItemState(item.getItemId(), DownloadState.COMPLETED);
            listenerHandler.post(new Runnable() {
                @Override
                public void run() {
                    downloadStateListener.onDownloadComplete(item);
                }
            });
        } else {
            listenerHandler.post(new Runnable() {
                @Override
                public void run() {
                    downloadStateListener.onProgressChange(item, totalBytes);
                }
            });
        }
    }
    
    private DownloadStateListener noopListener = new DownloadStateListener() {
        @Override
        public void onDownloadComplete(DownloadItem item) {

        }

        @Override
        public void onProgressChange(DownloadItem item, long downloadedBytes) {

        }

        @Override
        public void onDownloadStart(DownloadItem item) {

        }

        @Override
        public void onDownloadPause(DownloadItem item) {

        }

        @Override
        public void onDownloadFailure(DownloadItem item, Exception error) {

        }

        @Override
        public void onDownloadMetadata(DownloadItem item, Exception error) {

        }

        @Override
        public void onTracksAvailable(DownloadItem item, DownloadItem.TrackSelector trackSelector) {

        }
    };

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
        return localBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "*** onUnbind");
        stop();
        return super.onUnbind(intent);
    }

    private void startHandlerThreads() {
        // HandlerThread for calling the listener
        HandlerThread listenerThread = new HandlerThread("DownloadStateListener");
        listenerThread.start();
        listenerHandler = new Handler(listenerThread.getLooper());

        // HandlerThread for handling task progress updates
        listenerThread = new HandlerThread("DownloadTaskListener");
        listenerThread.start();
        taskProgressHandler = new Handler(listenerThread.getLooper());
    }
    
    private void stopHandlerThreads() {
        listenerHandler.getLooper().quit();
        listenerHandler = null;
        taskProgressHandler.getLooper().quit();
        taskProgressHandler = null;
    }

    void pauseItemDownload(String itemId) {
        if (itemId != null) {
            futureMap.cancelItem(itemId);
        } else {
            futureMap.cancelAll();
        }
        // Maybe add PAUSE_ALL with executorService.purge(); and remove futures
    }

    void downloadChunks(ArrayList<DownloadTask> chunks, String itemId) {
        if (chunks == null) {
            return;
        }
        for (DownloadTask task : chunks) {
            task.itemId = itemId;
            FutureTask future = futureTask(itemId, task);
            executorService.execute(future);
            futureMap.add(itemId, future);
        }
    }

    public void updateItemInfoInDB(DownloadItemImp item, String... columns) {
        if (database != null) {
            database.updateItemInfo(item, columns);
        }
    }

    public void updateItemEstimatedSizeInDB(DownloadItemImp item) {
        updateItemInfoInDB(item, Database.COL_ITEM_ESTIMATED_SIZE);
    }

    private void assertStarted() {
        if (!started) {
            throw new IllegalStateException("Service not started");
        }
    }
    
    public void start() {

        Log.d(TAG, "start()");

        File dataDir = new File(context.getFilesDir(), "dtg/clear");
        makeDirs(dataDir, "provider data directory");

        File extFilesDir = context.getExternalFilesDir(null);
        if (extFilesDir != null) {
            downloadsDir = new File(extFilesDir, "dtg/clear");
            makeDirs(downloadsDir, "provider downloads");
            if(settings.createNoMediaFileInDownloadsDir) {
                File noMediafileExternal = new File(extFilesDir, NO_MEDIA_EMPTY_FILE);
                try {
                    noMediafileExternal.createNewFile();
                } catch (IOException e) {
                    throw new IllegalStateException("Can't create nomedia file at " + noMediafileExternal);
                }
            }
        } else {
            downloadsDir = dataDir;
        }

        File dbFile = new File(dataDir, "downloads.db");

        database = new Database(dbFile, context);

        startHandlerThreads();

        executorService = Executors.newFixedThreadPool(settings.maxConcurrentDownloads);

        started = true;
    }

    public void stop() {
        
        Log.d(TAG, "stop()");

        if (!started) {
            return;
        }

        stopping = true;

        for (DownloadItemImp item : getDownloads(new DownloadState[]{DownloadState.IN_PROGRESS})) {
            pauseItemDownload(item.getItemId());
        }
        
        taskProgressHandler.getLooper().quit();

        executorService.shutdownNow();
        try {
            executorService.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "stop: awaitTerminationInterrupted", e);
        }
        stopHandlerThreads();

        // close db
        database.close();
        database = null;

        started = false;
        stopping = false;
    }

    private void makeDirs(File dataDir, String name) {
        Utils.mkdirsOrThrow(dataDir);
        if (!dataDir.isDirectory()) {
            Log.e(TAG, "Failed to create " + name + " -- " + dataDir);
            throw new IllegalStateException("Can't continue without " + name + " -- " + dataDir);
        }
    }

    public void loadItemMetadata(final DownloadItemImp item) {
        assertStarted();
        
        AsyncTask.execute(new Runnable() {
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
        });
    }

    public File getItemDataDir(String itemId) {
        assertStarted();

        return new File(downloadsDir, "items/" + itemId + "/data");    // TODO: make sure name is safe.
    }

    private void downloadMetadata(DownloadItemImp item) throws IOException {

        // Handle service being stopped
        if (isServiceStopped()) {
            Log.w(TAG, "Service not started or being stopped, can't start download");
            return;
        }

        File itemDataDir = getItemDataDir(item.getItemId());
        String contentURL = item.getContentURL();
        if (contentURL.startsWith("widevine")) {
            contentURL = contentURL.replaceFirst("widevine", "http");
        }
        Uri contentUri = Uri.parse(contentURL);
        URL url = new URL(contentURL);
        String fileName = contentUri.getLastPathSegment();

        AbrDownloader downloader = null;

        if (fileName.endsWith(AssetFormat.hls.extension())) {
            downloader = new HlsDownloader(item);
        } else if (fileName.endsWith(AssetFormat.dash.extension())) {
            downloader = new DashDownloader(item);
        }

        if (downloader == null) {
            downloadMetadataSimple(url, item, itemDataDir);
        } else {
            downloadMetadataAbr(downloader, item);
        }
    }

    private void downloadMetadataAbr(AbrDownloader downloader, DownloadItemImp item) throws IOException {

        downloader.initForCreate();

        // FIXME: 08/07/2018 It looks like this code has to move to AbrDownloader

        DownloadItem.TrackSelector trackSelector = downloader.getTrackSelector();

        item.setTrackSelector(trackSelector);

        downloadStateListener.onTracksAvailable(item, trackSelector);

        downloader.apply();

        item.setTrackSelector(null);


        List<BaseTrack> availableTracks = Utils.flattenTrackList(downloader.getAvailableTracksMap());
        List<BaseTrack> selectedTracks = downloader.getSelectedTracksFlat();

        addTracksToDB(item, availableTracks, selectedTracks);

        long estimatedDownloadSize = downloader.getEstimatedDownloadSize();
        item.setEstimatedSizeBytes(estimatedDownloadSize);

        LinkedHashSet<DownloadTask> downloadTasks = downloader.getDownloadTasks();
        //Log.d(TAG, "tasks:" + downloadTasks);

        item.setPlaybackPath(downloader.storedLocalManifestName());

        addDownloadTasksToDB(item, new ArrayList<>(downloadTasks));
    }

    private boolean isServiceStopped() {
        return stopping || !started;
    }

    private void downloadMetadataSimple(URL url, DownloadItemImp item, File itemDataDir) throws IOException {

        long length = Utils.httpHeadGetLength(url);

        String fileNameFullPath = Utils.getHashedFileName(url.getPath());
        File targetFile = new File(itemDataDir, fileNameFullPath);
        DownloadTask downloadTask = new DownloadTask(url, targetFile);

        item.setEstimatedSizeBytes(length);
        item.setPlaybackPath(fileNameFullPath);

        addDownloadTasksToDB(item, Collections.singletonList(downloadTask));
    }

    public void addDownloadTasksToDB(DownloadItemImp item, List<DownloadTask> tasks) {
        
        // Filter-out things that are already 
        
        database.addDownloadTasksToDB(item, tasks);
    }

    public void addTracksToDB(DownloadItemImp item, List<BaseTrack> availableTracks, List<BaseTrack> selectedTracks) {
        database.addTracks(item, availableTracks, selectedTracks);
    }

    public DownloadState startDownload(final String itemId) {
        assertStarted();
        if (TextUtils.isEmpty(itemId)) {
            throw new IllegalStateException("Can't download empty itemId");
        }

        final DownloadItemImp item = findItemImpl(itemId);
        if (item == null) {
            throw new IllegalStateException("Can't find item in db");
        }

        if (item.getState() == DownloadState.NEW) {
            throw new IllegalStateException("Can't start download while itemState == NEW");
        }

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

    public void pauseDownload(final DownloadItemImp item) {
        assertStarted();

        if (item != null) {
            int countPendingFiles = database.countPendingFiles(item.getItemId());
            if (countPendingFiles > 0) {

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

    public void resumeDownload(DownloadItemImp item) {
        assertStarted();

        // resume should be considered as download start

        DownloadState itemState = startDownload(item.getItemId());
        item.updateItemState(itemState);
    }

    public void removeItem(DownloadItemImp item) {
        assertStarted();

        if (item == null) {
            return;
        }

        
        pauseDownload(item);
        
        // pauseDownload takes time to interrupt all downloads, and the downloads report their
        // progress. Keep a list of the items that were removed in this session and ignore their
        // progress.
        removedItems.add(item.getItemId());
        
        
        deleteItemFiles(item.getItemId());
        database.removeItemFromDB(item);
    }

    private void deleteItemFiles(String item) {
        String path = downloadsDir + "/items/" + item;
        File file = new File(path);

        Utils.deleteRecursive(file);
    }

    private DownloadItemImp findItemImpl(String itemId) {

        // TODO: cache items in memory?
        
        DownloadItemImp item = database.findItemInDB(itemId);
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
    
    public DownloadItemImp findItem(String itemId) {
        assertStarted();

        return findItemImpl(itemId);
    }

    public long getDownloadedItemSize(@Nullable String itemId) {
        return database.getDownloadedItemSize(itemId);
    }

    public DownloadItemImp createItem(String itemId, String contentURL) {
        assertStarted();
        
        // if this item was just removed, unmark it as removed.
        removedItems.remove(itemId);
        

        DownloadItemImp item = findItemImpl(itemId);
        // If item already exists, return null.
        if (item != null) {
            return null;    // don't create, DON'T return db item.
        }

        if (contentURL == null) {
            Log.e(TAG, "Can't add item with contentURL==null");
            return null;
        }

        item = new DownloadItemImp(itemId, contentURL);
        item.setState(DownloadState.NEW);
        item.setAddedTime(System.currentTimeMillis());
        File itemDataDir = getItemDataDir(itemId);
        
        makeDirs(itemDataDir, "item data directory");

        item.setDataDir(itemDataDir.getAbsolutePath());

        database.addItemToDB(item, itemDataDir);

        item.setProvider(this);
        return item;
    }

    public void updateItemState(String itemId, DownloadState state) {
        assertStarted();

        database.updateItemState(itemId, state);
    }

    public List<DownloadItemImp> getDownloads(DownloadState[] states) {
        assertStarted();

        ArrayList<DownloadItemImp> items = database.readItemsFromDB(states);

        for (DownloadItemImp item : items) {
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

        DownloadItemImp item = findItemImpl(itemId);
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
            listener = noopListener;
        }
        downloadStateListener = listener;
    }

    public long getEstimatedItemSize(String itemId) {
        assertStarted();

        return database.getEstimatedItemSize(itemId);
    }

    public List<BaseTrack> readTracksFromDB(String itemId, TrackType trackType, BaseTrack.TrackState state, AssetFormat assetFormat) {
        return database.readTracks(itemId, trackType, state, assetFormat);
    }
    
    public void updateTracksInDB(String itemId, Map<TrackType, List<BaseTrack>> tracksMap, BaseTrack.TrackState state) {
        database.updateTracksState(itemId, Utils.flattenTrackList(tracksMap), state);
    }

    public int countPendingFiles(String itemId, @Nullable BaseTrack track) {
        return database.countPendingFiles(itemId, track != null ? track.getRelativeId() : null);
    }

    private FutureTask futureTask(final String itemId, final DownloadTask task) {
        task.setListener(mDownloadTaskListener);
        task.setDownloadSettings(settings);
        Callable<Void> callable = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                while (true) {
                    try {
                        task.download();
                        break;
                    } catch (HttpRetryException e) {
                        Log.d(TAG, "Task should be retried");
                        Thread.sleep(2000);
                        // continue
                    }
                }
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

    public void setDownloadSettings(ContentManager.Settings downloadSettings) {
        if (started) {
            throw new IllegalStateException("Can't change settings after start");
        }
        
        // Copy fields.
        this.settings = new ContentManager.Settings();
        this.settings.httpTimeoutMillis = downloadSettings.httpTimeoutMillis;
        this.settings.maxDownloadRetries = downloadSettings.maxDownloadRetries;
        this.settings.maxConcurrentDownloads = downloadSettings.maxConcurrentDownloads;
        this.settings.createNoMediaFileInDownloadsDir = downloadSettings.createNoMediaFileInDownloadsDir;
    }

    class LocalBinder extends Binder {
        DownloadService getService() {
            return DownloadService.this;
        }
    }
}
