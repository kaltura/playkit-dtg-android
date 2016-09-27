package com.kaltura.dtg.clear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.kaltura.android.exoplayer.hls.Variant;
import com.kaltura.dtg.DownloadItem;
import com.kaltura.dtg.DownloadProvider;
import com.kaltura.dtg.DownloadState;
import com.kaltura.dtg.DownloadStateListener;
import com.kaltura.dtg.Utils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Created by noamt on 5/17/15.
 */
class ClearDownloadProvider implements DownloadProvider {

    static final String TAG = "ClearDownloadProvider";
    private final Context mContext;
    private Database mDatabase;
    private File mDownloadsDir;
    private boolean mStarted;
    private final File mDbFile;
    private DownloadStateListener mDownloadStateListener;
    
    private Map<String, DownloadTask> mTaskMap;

    
    private final DownloadTask.Listener mDownloadTaskListener = new DownloadTask.Listener() {

        @Override
        public void onTaskProgress(String taskId, DownloadTask.State newState, int newBytes) {
            DownloadTask downloadTask = mTaskMap.get(taskId);
            if (downloadTask == null) {
                Log.e(TAG, "Can't find task by id: " + taskId);
                return;
            }
            String itemId = downloadTask.itemId;
            ClearDownloadItem item = findItemImpl(itemId);
            if (item == null) {
                Log.e(TAG, "Can't find item by id: " + itemId + "; taskId: " + taskId);
                return;
            }
            
            int pendingCount = -1;
            if (newState == DownloadTask.State.COMPLETED) {
                mDatabase.markTaskAsComplete(downloadTask);
                pendingCount = mDatabase.countPendingFiles(itemId);
                Log.i(TAG, "Pending tasks for item: " + pendingCount);
            }

            if (newState == DownloadTask.State.ERROR) {
                item.setBroken(true);
            }

            long totalBytes = item.incDownloadBytes(newBytes);
            updateItemInfoInDB(item, Database.COL_ITEM_DOWNLOADED_SIZE);

            if (pendingCount == 0) {
                // We finished the last (or only) chunk of the item.
                mDatabase.setDownloadFinishTime(itemId);

                // TODO: revisit this "broken" notion.
                if (item.isBroken()) {
                    item.setState(DownloadState.PAUSED);
                    mDatabase.updateItemState(item.getItemId(), DownloadState.PAUSED);
                    mDownloadStateListener.onDownloadStop(item);
                } else {
                    item.setState(DownloadState.COMPLETED);
                    mDatabase.updateItemState(item.getItemId(), DownloadState.COMPLETED);
                    mDownloadStateListener.onDownloadComplete(item);
                }
            } else {
                mDownloadStateListener.onProgressChange(item, totalBytes);
            }
        }
    };

    void updateItemInfoInDB(ClearDownloadItem item, String... columns) {
        mDatabase.updateItemInfo(item, columns);
    }

    private BroadcastReceiver mServiceBroadcastReceiver;

    ClearDownloadProvider(Context appContext) {
        this.mContext = appContext;
        
        File dataDir = new File(mContext.getFilesDir(), "dtg/clear");
        dataDir.mkdirs();
        if (!dataDir.isDirectory()) {
            Log.e(TAG, "Failed to create provider data directory " + dataDir);
            throw new IllegalStateException("Can't continue without the data directory, " + dataDir);
        }

        File extFilesDir = mContext.getExternalFilesDir(null);
        if (extFilesDir != null) {
            mDownloadsDir = new File(extFilesDir, "dtg/clear");
            mDownloadsDir.mkdirs();
            if (!mDownloadsDir.isDirectory()) {
                Log.e(TAG, "Failed to create provider downloads directory " + mDownloadsDir);
                throw new IllegalStateException("Can't continue without the downloads directory, " + mDownloadsDir);
            }

        } else {
            mDownloadsDir = dataDir;
        }


        mDbFile = new File(dataDir, "downloads.db");

        mTaskMap = new HashMap<>();
        mTaskMap = Collections.synchronizedMap(mTaskMap);
    }

    private void startServiceBroadcastReceiver(Context context) {

        mServiceBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String taskId = intent.getStringExtra(ClearDownloadService.EXTRA_DOWNLOAD_TASK_ID);
                int newBytes = intent.getIntExtra(ClearDownloadService.EXTRA_DOWNLOAD_TASK_NEW_BYTES, 0);
                DownloadTask.State newState = DownloadTask.State.fromOrdinal(
                        intent.getIntExtra(ClearDownloadService.EXTRA_DOWNLOAD_TASK_STATE, DownloadTask.State.IDLE.ordinal()));
                mDownloadTaskListener.onTaskProgress(taskId, newState, newBytes);
            }
        };

        LocalBroadcastManager bManager = LocalBroadcastManager.getInstance(context);
        IntentFilter intentFilter = new IntentFilter(ClearDownloadService.ACTION_NOTIFY_DOWNLOAD_PROGRESS);
        bManager.registerReceiver(mServiceBroadcastReceiver, intentFilter);
    }

    private void stopServiceBroadcastReceiver() {
        if (mServiceBroadcastReceiver != null) {
            LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(mContext);
            lbm.unregisterReceiver(mServiceBroadcastReceiver);
            mServiceBroadcastReceiver = null;
        }
    }
    
    private void assertStarted() {
        if (!mStarted) {
            throw new IllegalStateException("Service not started");
        }
    }

    @Override
    public void start() {

        Log.d(TAG, "start()");

        if (!mStarted) {
            mDatabase = new Database(mDbFile, mContext);

            sendServiceRequest(ClearDownloadService.ACTION_START_SERVICE, null, null);
            
            startServiceBroadcastReceiver(mContext);

            mStarted = true;
        }
    }

    @Override
    public void stop() {
        
        Log.d(TAG, "stop()");
        
        if (mStarted) {
            // stop service
            mContext.stopService(new Intent(mContext, ClearDownloadService.class));

            // close db
            mDatabase.close();
            mDatabase = null;
            
            // stop broadcast receiver
            stopServiceBroadcastReceiver();

            mStarted = false;
        }
    }

    @Override
    public void loadItemMetadata(final DownloadItem item) {
        assertStarted();
        
        final ClearDownloadItem clearItem = (ClearDownloadItem) item;

        new Thread() {
            @Override
            public void run() {
                try {
                    downloadMetadata(clearItem);
                    clearItem.setState(DownloadState.INFO_LOADED);
                    updateItemInfoInDB(clearItem,
                            Database.COL_ITEM_STATE, Database.COL_ITEM_ESTIMATED_SIZE,
                            Database.COL_ITEM_PLAYBACK_PATH);
                    mDownloadStateListener.onDownloadMetadata(item, null);

                } catch (IOException e) {
                    Log.e(TAG, "Failed to download metadata for " + clearItem.getItemId(), e);
                    mDownloadStateListener.onDownloadMetadata(item, e);
                }
            }
        }.start();
    }

    public File getItemDataDir(String itemId) {
        assertStarted();

        return new File(mDownloadsDir, "items/" + itemId + "/data");    // TODO: make sure name is safe.
    }

    private void downloadMetadata(ClearDownloadItem item) throws IOException {

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

    private void downloadMetadataDash(ClearDownloadItem item, File itemDataDir) throws IOException {

        final DashDownloader dashDownloader = new DashDownloadCreator(item.getContentURL(), itemDataDir);

        DownloadItem.TrackSelector trackSelector = dashDownloader.getTrackSelector();
        
        item.setTrackSelector(trackSelector);
        
        mDownloadStateListener.onTracksAvailable(item, trackSelector);
        
        dashDownloader.apply();

        item.setTrackSelector(null);


        List<DashTrack> availableTracks = dashDownloader.getAvailableTracks();
        List<DashTrack> selectedTracks = dashDownloader.getSelectedTracks();
        
        mDatabase.addTracks(item, availableTracks, selectedTracks);

        long estimatedDownloadSize = dashDownloader.getEstimatedDownloadSize();
        item.setEstimatedSizeBytes(estimatedDownloadSize);

        LinkedHashSet<DownloadTask> downloadTasks = dashDownloader.getDownloadTasks();
        Log.d(TAG, "tasks:" + downloadTasks);

        item.setPlaybackPath(dashDownloader.getPlaybackPath());

        addDownloadTasksToDB(item, new ArrayList<>(downloadTasks));
    }

    private void downloadMetadataSimple(URL url, ClearDownloadItem item, File itemDataDir) throws IOException {

        long length = Utils.httpHeadGetLength(url);

        File targetFile = new File(itemDataDir, url.getPath());
        DownloadTask downloadTask = new DownloadTask(url, targetFile);

        item.setEstimatedSizeBytes(length);
        item.setPlaybackPath(url.getPath().substring(1));

        ArrayList<DownloadTask> downloadTasks = new ArrayList<>(1);
        downloadTasks.add(downloadTask);

        addDownloadTasksToDB(item, downloadTasks);
    }

    void addDownloadTasksToDB(ClearDownloadItem item, List<DownloadTask> tasks) {
        
        // Filter-out things that are already 
        
        mDatabase.addDownloadTasksToDB(item, tasks);
    }

    private void downloadMetadataHLS(ClearDownloadItem item, File itemDataDir) throws IOException {
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

    @Override
    public DownloadState startDownload(String itemId) {
        assertStarted();
        
        ClearDownloadItem item = findItemImpl(itemId);

        mDownloadStateListener.onDownloadStart(item);
        
        // Read download tasks from db
        ArrayList<DownloadTask> chunksToDownload = mDatabase.readPendingDownloadTasksFromDB(itemId);

        if (chunksToDownload.isEmpty()) {
            mDatabase.updateItemState(itemId, DownloadState.COMPLETED);
            mDownloadStateListener.onDownloadComplete(item);
            return DownloadState.COMPLETED;
        } else {
            int size = chunksToDownload.size();
            for (int index = 0; index < size ; index += 10) {
                int toIndex = index + 10;
                if (toIndex >= size) {
                    toIndex = chunksToDownload.size();
                }
                ArrayList<DownloadTask> buffer = new ArrayList<>(chunksToDownload.subList(index, toIndex));
                sendServiceRequest(ClearDownloadService.ACTION_DOWNLOAD, buffer, itemId);
            }
            mDatabase.updateItemState(itemId, DownloadState.IN_PROGRESS);
            return DownloadState.IN_PROGRESS;
        }
    }

    private void sendServiceRequest(@NonNull String action, @Nullable ArrayList<DownloadTask> downloadTasks, @Nullable String itemId) {
        Intent intent = new Intent(mContext, ClearDownloadService.class);

        intent.setAction(action);
        if (downloadTasks != null) {
            intent.putParcelableArrayListExtra(ClearDownloadService.EXTRA_DOWNLOAD_TASKS, downloadTasks);
            for (DownloadTask downloadTask : downloadTasks) {
                mTaskMap.put(downloadTask.taskId, downloadTask);
            }
        }
        if (itemId != null) {
            intent.putExtra(ClearDownloadService.EXTRA_ITEM_ID, itemId);
        }
        mContext.startService(intent);
    }

    @Override
    public void pauseDownload(DownloadItem item) {
        assertStarted();

        ClearDownloadItem clearDownloadItem = (ClearDownloadItem) item;
        ArrayList<DownloadTask> downloadTasks;
        if (clearDownloadItem != null) {
            downloadTasks = mDatabase.readPendingDownloadTasksFromDB(clearDownloadItem.getItemId());
            if (downloadTasks.size() > 0) {

                sendServiceRequest(ClearDownloadService.ACTION_PAUSE_DOWNLOAD, null, item.getItemId());

                clearDownloadItem.setState(DownloadState.PAUSED);
                mDatabase.updateItemState(clearDownloadItem.getItemId(), DownloadState.PAUSED);
                mDownloadStateListener.onDownloadPause(clearDownloadItem);
            }
        }
    }

    @Override
    public void resumeDownload(DownloadItem item) {
        assertStarted();

        // resume should be considered as download start

        DownloadState itemState = startDownload(item.getItemId());
        ((ClearDownloadItem) item).updateItemState(itemState);
    }

    @Override
    public void removeItem(DownloadItem item) {
        assertStarted();

        if (item == null) {
            return;
        }
        pauseDownload(item);
        deleteItemFiles(item.getItemId());
        mDatabase.removeItemFromDB((ClearDownloadItem) item);
    }


    private void deleteItemFiles(String item) {
        String path = mDownloadsDir + "/items/" + item;
        File file = new File(path);

        Utils.deleteRecursive(file);
    }

    private ClearDownloadItem findItemImpl(String itemId) {

        // TODO: cache items in memory?
        
        ClearDownloadItem item = mDatabase.findItemInDB(itemId);
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
    @Override
    public DownloadItem findItem(String itemId) {
        assertStarted();

        return findItemImpl(itemId);
    }

    @Override
    public long getDownloadedItemSize(@Nullable String itemId) {
        return mDatabase.getDownloadedItemSize(itemId);
    }

    @Override
    public DownloadItem createItem(String itemId, String contentURL) {
        assertStarted();

        ClearDownloadItem item = findItemImpl(itemId);
        // If item already exists, return null.
        if (item != null) {
            return null;    // don't create, DON'T return db item.
        }

        if (contentURL == null) {
            Log.e(TAG, "Can't add item with contentURL==null");
            return null;
        }

        item = new ClearDownloadItem(itemId, contentURL);
        item.setState(DownloadState.NEW);
        item.setAddedTime(System.currentTimeMillis());
        File itemDataDir = getItemDataDir(itemId);
        itemDataDir.mkdirs();
        if (!itemDataDir.isDirectory()) {
            Log.e(TAG, "Failed to create item data directory " + itemDataDir);
            throw new IllegalStateException("Can't continue without item data directory, " + itemDataDir);
        }

        item.setDataDir(itemDataDir.getAbsolutePath());

        mDatabase.addItemToDB(item, itemDataDir);

        item.setProvider(this);
        return item;
    }

    @Override
    public void updateItemState(String itemId, DownloadState state) {
        assertStarted();

        mDatabase.updateItemState(itemId, state);
    }

    @Override
    public List<DownloadItem> getDownloads(DownloadState[] states) {
        assertStarted();

        ArrayList<? extends DownloadItem> items = mDatabase.readItemsFromDB(states);

        for (DownloadItem item : items) {
            ((ClearDownloadItem)item).setProvider(this);
        }

        return Collections.unmodifiableList(items);
    }
    @Override
    public String getPlaybackURL(String itemId) {
        File localFile = getLocalFile(itemId);
        if (localFile == null) {
            return null;
        }
        return Uri.fromFile(localFile).toString();
    }
    
    @Override
    public File getLocalFile(String itemId) {
        assertStarted();

        ClearDownloadItem item = findItemImpl(itemId);
        if (item == null) {
            return null;
        }

        String playbackPath = item.getPlaybackPath();
        if (playbackPath == null) {
            return null;
        }

        return new File(item.getDataDir(), playbackPath);
    }
    
    @Override
    public void dumpState() {
    }

    @Override
    public void setDownloadStateListener(DownloadStateListener listener) {
        if (listener == null) {
            listener = DownloadStateListener.noopListener;
        }
        mDownloadStateListener = listener;
    }

    @Override
    public long getEstimatedItemSize(String itemId) {
        assertStarted();

        return mDatabase.getEstimatedItemSize(itemId);
    }
    
    List<DashTrack> readTracksFromDB(String itemId, DownloadItem.TrackType trackType, DashDownloader.TrackState state) {
        return mDatabase.readTracks(itemId, trackType, state);
    }

    void updateTracksInDB(String itemId, Map<DownloadItem.TrackType, List<DashTrack>> tracksMap, DashDownloader.TrackState state) {
        mDatabase.updateTracksState(itemId, DashDownloader.flattenTrackList(tracksMap), state);
    }
}


