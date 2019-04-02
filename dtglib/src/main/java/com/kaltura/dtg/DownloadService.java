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
import android.util.Log;

import com.kaltura.dtg.DownloadItem.TrackType;

import java.io.File;
import java.io.IOException;
import java.net.HttpRetryException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static android.os.SystemClock.elapsedRealtime;


public class DownloadService extends Service {

    private static final String TAG = "DownloadService";

    private final Context context;  // allow mocking
    private final LocalBinder localBinder = new LocalBinder();
    private Database database;
    private DownloadRequestParams.Adapter adapter;
    private boolean started;
    private boolean stopping;
    private DownloadStateListener downloadStateListener;
    private ExecutorService executorService;
    private final ItemFutureMap futureMap = new ItemFutureMap();
    private Handler listenerHandler = null;

    private Handler taskProgressHandler = null;
    ContentManager.Settings settings;

    private final Set<String> removedItems = new HashSet<>();

    private ItemCache itemCache = new ItemCache();

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

    private final DownloadStateListener noopListener = new DownloadStateListener() {
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

    public DownloadService(Context context) {
        this.context = context;
    }

    public DownloadService() {
        this.context = this;
    }

    private void onTaskProgress(DownloadTask task, DownloadTask.State newState, int newBytes, final Exception stopError) {

        if (stopping) {
            return;
        }

        String itemId = task.itemId;

        if (removedItems.contains(itemId)) {
            // Ignore this report.
            return;
        }

        final DownloadItemImp item = itemCache.get(itemId);
        if (item == null) {
            Log.e(TAG, "Can't find item by id: " + itemId + "; taskId: " + task.taskId);
            return;
        }

        int pendingCount = -1;
        if (newState == DownloadTask.State.COMPLETED) {
            database.markTaskAsComplete(task);
            pendingCount = countPendingFiles(itemId, null);
            Log.i(TAG, "Pending tasks for item: " + pendingCount + "; finished " + task.url.getLastPathSegment());
        }

        if (newState == DownloadTask.State.ERROR) {
            Log.d(TAG, "Task has failed; cancelling item " + itemId + " offending URL: " + task.url);

            itemCache.updateItemState(item, DownloadState.FAILED);

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
        itemCache.markDirty(itemId);

        if (pendingCount == 0) {
            // We finished the last (or only) chunk of the item.
            database.setDownloadFinishTime(itemId);

            itemCache.updateItemState(item, DownloadState.COMPLETED);
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

    @Override
    public void onCreate() {
        Log.d(TAG, "*** onCreate");
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


        itemCache.scheduleCacheManager(taskProgressHandler);
    }

    private void stopHandlerThreads() {
        listenerHandler.getLooper().quit();
        listenerHandler = null;
        taskProgressHandler.getLooper().quit();
        taskProgressHandler = null;
    }

    private void pauseItemDownload(String itemId) {
        if (itemId != null) {
            futureMap.cancelItem(itemId);
        } else {
            futureMap.cancelAll();
        }
        // Maybe add PAUSE_ALL with executorService.purge(); and remove futures
    }

    private void downloadChunks(ArrayList<DownloadTask> chunks, String itemId) {
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

    private void updateItemInfoInDB(DownloadItemImp item, String... columns) {
        itemCache.updateItemInfo(item, columns);
        if (database != null) {
            database.updateItemInfo(item, columns);

        }
    }

    void updateItemEstimatedSizeInDB(DownloadItemImp item) {
        updateItemInfoInDB(item, Database.COL_ITEM_ESTIMATED_SIZE);
    }

    void updateItemDurationInDB(DownloadItemImp item) {
        updateItemInfoInDB(item, Database.COL_ITEM_DURATION);
    }

    private void assertStarted() {
        if (!started) {
            throw new IllegalStateException("Service not started");
        }
    }

    void start() {

        Log.d(TAG, "start()");


        File dbFile = new File(Storage.getDataDir(), "downloads.db");

        database = new Database(dbFile, context);

        startHandlerThreads();

        executorService = Executors.newFixedThreadPool(settings.maxConcurrentDownloads);

        started = true;
    }

    void stop() {

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

    void loadItemMetadata(final DownloadItemImp item) {
        assertStarted();

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    newDownload(item);
                    itemCache.updateItemState(item, DownloadState.INFO_LOADED);
                    updateItemInfoInDB(item,
                            Database.COL_ITEM_ESTIMATED_SIZE,
                            Database.COL_ITEM_PLAYBACK_PATH);
                    downloadStateListener.onDownloadMetadata(item, null);

                } catch (IOException e) {
                    Log.e(TAG, "Failed to download metadata for " + item.getItemId(), e);
                    downloadStateListener.onDownloadMetadata(item, e);
                }
            }
        });
    }

    private File getItemDataDir(String itemId) {
        assertStarted();

        return new File(Storage.getDownloadsDir(), "items/" + itemId + "/data");
    }

    private boolean isServiceStopped() {
        return stopping || !started;
    }

    private void newDownload(DownloadItemImp item) throws IOException {

        // Handle service being stopped
        if (isServiceStopped()) {
            Log.w(TAG, "Service not started or being stopped, can't start download");
            return;
        }

        if (item.getAssetFormat().isAbr()) {
            newAbrDownload(item);
        } else {
            newSimpleDownload(item);
        }
    }

    private void newAbrDownload(DownloadItemImp item) throws IOException {
        final AbrDownloader downloader = AbrDownloader.newDownloader(item, settings);
        if (downloader == null) {
            return;
        }

        downloader.create(this, downloadStateListener);
    }

    private void newSimpleDownload(DownloadItemImp item) throws IOException {

        String contentURL = item.getContentURL();
        if (contentURL.startsWith("widevine")) {
            contentURL = contentURL.replaceFirst("widevine", "http");
        }
        Uri url = Uri.parse(contentURL);

        long length = Utils.httpHeadGetLength(url);

        String fileNameFullPath = Utils.getHashedFileName(url.getPath());
        File targetFile = new File(item.getDataDir(), fileNameFullPath);
        DownloadTask downloadTask = new DownloadTask(url, targetFile, 1);

        item.setEstimatedSizeBytes(length);
        item.setPlaybackPath(fileNameFullPath);

        addDownloadTasksToDB(item, Collections.singletonList(downloadTask));
    }

    void addDownloadTasksToDB(DownloadItemImp item, List<DownloadTask> tasks) {

        // Filter-out things that are already 

        database.addDownloadTasksToDB(item, tasks);
    }

    void addTracksToDB(DownloadItemImp item, List<BaseTrack> availableTracks, List<BaseTrack> selectedTracks) {
        database.addTracks(item, availableTracks, selectedTracks);
    }

    DownloadState startDownload(final DownloadItemImp item) {
        assertStarted();

        // Refresh item state

        if (item.getState() == DownloadState.NEW) {
            final DownloadItemImp downloadItemImp = itemCache.get(item.getItemId());
            throw new IllegalStateException("Can't start download while itemState == NEW");
        }

        itemCache.updateItemState(item, DownloadState.IN_PROGRESS);

        listenerHandler.post(new Runnable() {
            @Override
            public void run() {
                downloadStateListener.onDownloadStart(item);
            }
        });

        // Read download tasks from db
        ArrayList<DownloadTask> chunksToDownload = database.readPendingDownloadTasksFromDB(item.getItemId());

        if (chunksToDownload.isEmpty()) {
            itemCache.updateItemState(item, DownloadState.COMPLETED);

            listenerHandler.post(new Runnable() {
                @Override
                public void run() {
                    downloadStateListener.onDownloadComplete(item);
                }
            });

        } else {

            if (chunksToDownload.get(0).order == DownloadTask.UNKNOWN_ORDER) {
                // Shuffle to mix large and small files together, making download speed look smooth.
                // Otherwise, all small files (subtitles, keys) are downloaded together. Because of
                // http request overhead the download speed is very slow when downloading the small
                // files and fast when downloading the large ones (video).
                // This is not needed if the tasks are correctly ordered.
                Collections.shuffle(chunksToDownload, new Random(42));
            }

            downloadChunks(chunksToDownload, item.getItemId());
            itemCache.updateItemState(item, DownloadState.IN_PROGRESS);
        }

        return item.getState();
    }

    void pauseDownload(final DownloadItemImp item) {
        assertStarted();

        if (item != null) {
            int countPendingFiles = countPendingFiles(item.getItemId());
            if (countPendingFiles > 0) {

                pauseItemDownload(item.getItemId());

                itemCache.updateItemState(item, DownloadState.PAUSED);

                listenerHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        downloadStateListener.onDownloadPause(item);
                    }
                });
            }
        }
    }

    void resumeDownload(DownloadItemImp item) {
        assertStarted();

        // resume should be considered as download start

        DownloadState itemState = startDownload(item);
        itemCache.updateItemState(item, itemState);
    }

    void removeItem(DownloadItemImp item) {
        assertStarted();

        if (item == null) {
            return;
        }


        pauseDownload(item);

        // pauseDownload takes time to interrupt all downloads, and the downloads report their
        // progress. Keep a list of the items that were removed in this session and ignore their
        // progress.
        final String itemId = item.getItemId();
        removedItems.add(itemId);


        deleteItemFiles(itemId);
        itemCache.remove(itemId);
    }

    private void deleteItemFiles(String itemId) {
        File itemDir = new File(Storage.getDownloadsDir(), "items/" + itemId);

        Utils.deleteRecursive(itemDir);
    }

    /**
     * Find and return an item.
     *
     * @return An item identified by itemId, or null if not found.
     */
    DownloadItemImp findItem(String itemId) {
        assertStarted();

        return itemCache.get(itemId);
    }

    long getDownloadedItemSize(@Nullable String itemId) {
        final DownloadItemImp item = itemCache.get(itemId);
        if (item != null) {
            return item.getDownloadedSizeBytes();
        }

        return 0;
    }

    DownloadItemImp createItem(String itemId, String contentURL) throws Utils.DirectoryNotCreatableException {
        assertStarted();

        // if this item was just removed, unmark it as removed.
        removedItems.remove(itemId);


        DownloadItemImp item = itemCache.get(itemId);
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

        Utils.mkdirsOrThrow(itemDataDir);

        item.setDataDir(itemDataDir.getAbsolutePath());

        database.addItemToDB(item, itemDataDir);

        item.setService(this);
        return item;
    }

    List<DownloadItemImp> getDownloads(DownloadState[] states) {
        assertStarted();

        ArrayList<DownloadItemImp> items = database.readItemsFromDB(states);

        for (DownloadItemImp item : items) {
            item.setService(this);
        }

        return Collections.unmodifiableList(items);
    }

    String getPlaybackURL(String itemId) {
        File localFile = getLocalFile(itemId);
        if (localFile == null) {
            return null;
        }
        return Uri.fromFile(localFile).toString();
    }

    File getLocalFile(String itemId) {
        assertStarted();

        DownloadItemImp item = itemCache.get(itemId);
        if (item == null) {
            return null;
        }

        String playbackPath = item.getPlaybackPath();
        if (playbackPath == null) {
            return null;
        }

        return new File(item.getDataDir(), playbackPath);
    }

    void setDownloadStateListener(DownloadStateListener listener) {
        if (listener == null) {
            listener = noopListener;
        }
        downloadStateListener = listener;
    }

    long getEstimatedItemSize(String itemId) {
        assertStarted();

        return database.getEstimatedItemSize(itemId);
    }

    List<BaseTrack> readTracksFromDB(String itemId, TrackType trackType, BaseTrack.TrackState state, AssetFormat assetFormat) {
        return database.readTracks(itemId, trackType, state, assetFormat);
    }

    void updateTracksInDB(String itemId, Map<TrackType, List<BaseTrack>> tracksMap, BaseTrack.TrackState state) {
        database.updateTracksState(itemId, Utils.flattenTrackList(tracksMap), state);
    }

    int countPendingFiles(String itemId, @Nullable BaseTrack track) {
        return database.countPendingFiles(itemId, track != null ? track.getRelativeId() : null);
    }

    private int countPendingFiles(String itemId) {
        return database.countPendingFiles(itemId, null);
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

    void setSettings(ContentManager.Settings settings) {
        if (started) {
            throw new IllegalStateException("Can't change settings after start");
        }

        this.settings = settings;
    }

    class LocalBinder extends Binder {
        DownloadService getService() {
            return DownloadService.this;
        }
    }

    private class ItemCache {
        private Map<String, DownloadItemImp> cache = new ConcurrentHashMap<>();
        private Set<String> dbFlushNeeded = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
        private Map<String, Long> itemLastUseTime = new ConcurrentHashMap<>();

        private void markDirty(String itemId) {
            dbFlushNeeded.add(itemId);
        }

        private void scheduleCacheManager(Handler handler) {
            handler.post(new Runnable() {
                @Override
                public void run() {

                    // Flush dirty items to db
                    for (String itemId : dbFlushNeeded) {
                        final DownloadItemImp item = cache.get(itemId);
                        updateItemInfoInDB(item, Database.COL_ITEM_DOWNLOADED_SIZE);
                    }

                    // Remove unused items
                    final Iterator<Map.Entry<String, Long>> iterator = itemLastUseTime.entrySet().iterator();
                    while (iterator.hasNext()) {
                        final Map.Entry<String, Long> entry = iterator.next();
                        if (elapsedRealtime() - entry.getValue() > 60000) {
                            final String itemId = entry.getKey();
                            iterator.remove();
                            cache.remove(itemId);
                            dbFlushNeeded.remove(itemId);
                        }
                    }

                    taskProgressHandler.postDelayed(this, 200);
                }
            });
        }

        private DownloadItemImp get(String itemId) {
            DownloadItemImp item = cache.get(itemId);
            if (item != null) {
                itemLastUseTime.put(itemId, elapsedRealtime());
                return item;
            }

            item = database.findItemInDB(itemId);
            if (item != null) {
                item.setService(DownloadService.this);
                cache.put(itemId, item);
                itemLastUseTime.put(itemId, elapsedRealtime());
            }

            return item;
        }

        private void remove(String itemId) {
            cache.remove(itemId);
            itemLastUseTime.remove(itemId);
            dbFlushNeeded.remove(itemId);
            database.removeItemFromDB(itemId);
        }

        private void updateItemState(DownloadItemImp item, DownloadState state) {
            item.setState(state);

            updateItemInfo(item, new String[]{Database.COL_ITEM_STATE});
        }

        private void updateItemInfo(DownloadItemImp item, String[] columns) {
            final DownloadItemImp cachedItem = cache.get(item.getItemId());
            if (cachedItem != null) {
                // Update cached item too
                for (String column : columns) {
                    switch (column) {
                        case Database.COL_ITEM_ESTIMATED_SIZE:
                            cachedItem.setEstimatedSizeBytes(item.getEstimatedSizeBytes());
                            break;
                        case Database.COL_ITEM_PLAYBACK_PATH:
                            cachedItem.setPlaybackPath(item.getPlaybackPath());
                            break;
                        case Database.COL_ITEM_DOWNLOADED_SIZE:
                            cachedItem.setDownloadedSizeBytes(item.getDownloadedSizeBytes());
                            break;
                        case Database.COL_ITEM_STATE:
                            cachedItem.setState(item.getState());
                            break;
                    }
                }
            }
            database.updateItemInfo(item, columns);
        }
    }
}
