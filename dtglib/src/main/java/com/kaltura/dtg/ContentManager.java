package com.kaltura.dtg;

import android.content.Context;

import java.io.File;
import java.util.HashSet;
import java.util.List;


/**
 * Created by noamt on 5/10/15.
 */

public class ContentManager {

    private static ContentManager sInstance;
    private Context mContext;
    private DownloadProvider mProvider;
    private File mItemsDir;
    private boolean mStarted;
    
    private final HashSet<DownloadStateListener> mStateListeners = new HashSet<>(1);
    private final DownloadStateListener mDownloadStateRelay = new DownloadStateListener() {

        // Pass the state to all listeners.

        @Override
        public void onDownloadComplete(DownloadItem item) {
            for (DownloadStateListener stateListener : mStateListeners) {
                stateListener.onDownloadComplete(item);
            }
        }

        @Override
        public void onProgressChange(DownloadItem item, long downloadedBytes) {
            for (DownloadStateListener stateListener : mStateListeners) {
                stateListener.onProgressChange(item, downloadedBytes);
            }
        }

        @Override
        public void onDownloadStart(DownloadItem item) {
            for (DownloadStateListener stateListener : mStateListeners) {
                stateListener.onDownloadStart(item);
            }
        }

        @Override
        public void onDownloadPause(DownloadItem item) {
            for (DownloadStateListener stateListener : mStateListeners) {
                stateListener.onDownloadPause(item);
            }
        }

        @Override
        public void onDownloadStop(DownloadItem item) {
            for (DownloadStateListener stateListener : mStateListeners) {
                stateListener.onDownloadStop(item);
            }
        }

        @Override
        public void onDownloadMetadata(DownloadItem item, Exception error) {
            for (DownloadStateListener stateListener : mStateListeners) {
                stateListener.onDownloadMetadata(item, error);
            }
        }

        @Override
        public void onTracksAvailable(DownloadItem item, DownloadItem.TrackSelector trackSelector) {
            for (DownloadStateListener stateListener : mStateListeners) {
                stateListener.onTracksAvailable(item, trackSelector);
            }
        }
    };
    
    
    private ContentManager(Context context) {
        mContext = context.getApplicationContext();
        File filesDir = mContext.getFilesDir();
        mItemsDir = new File(filesDir, "dtg/items");

        // make sure all directories are there.
        filesDir.mkdirs();
        mItemsDir.mkdirs();
        
        AppBuildConfig.init(context);
    }
    

    // Public API
    
    public static ContentManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (ContentManager.class) {
                if (sInstance == null) {
                    sInstance = new ContentManager(context);
                }
            }
        }
        return sInstance;
    }

    /**
     * Add download listener.
     * @param listener
     */
    public void addDownloadStateListener(DownloadStateListener listener) {
        mStateListeners.add(listener);
    }

    /**
     * Remove download listener.
     * @param listener
     */
    public void removeDownloadStateListener(DownloadStateListener listener) {
        mStateListeners.remove(listener);
    }

    /**
     * Stop the downloader. Stops all running downloads, but keep them in IN_PROGRESS state.
     */
    public void stop() {
        mProvider.stop();
        mProvider = null;
        mStarted = false;
    }

    /**
     * Start the download manager. Starts all downloads that were in IN_PROGRESS state when the 
     * manager was stopped. Add listeners before calling this method.
     */
    public void start() {

        if (mStarted) {
            return;
        }

        mProvider = DownloadProviderFactory.getProvider(mContext);
        mProvider.setDownloadStateListener(mDownloadStateRelay);
        mProvider.start();

        // Resume all downloads that were in progress on stop.
        List<DownloadItem> downloads = getDownloads(DownloadState.IN_PROGRESS);
        for (DownloadItem download : downloads) {
            download.startDownload();
        }

        mStarted = true;
    }

    /**
     * Pause all downloads (set their state to PAUSE and stop downloading).
     */
    public void pauseDownloads() {
        List<DownloadItem> downloads = getDownloads(DownloadState.IN_PROGRESS);
        for (DownloadItem item : downloads) {
            mProvider.pauseDownload(item);
        }
    }

    /**
     * Resume all PAUSED downloads.
     */
    public void resumeDownloads() {
        List<DownloadItem> downloads = getDownloads(DownloadState.PAUSED);
        for (DownloadItem item : downloads) {
            mProvider.resumeDownload(item);
        }
    }

    /**
     * Find and return an item.
     *
     * @return An item identified by itemId, or null if not found.
     */
    public DownloadItem findItem(String itemId) {
        return mProvider.findItem(itemId);
    }

    /**
     * Returns the number of downloaded bytes. 
     * @param itemId item. If null, returns the sum from all items.
     * @return
     */
    public long getDownloadedItemSize(String itemId) {
        return mProvider.getDownloadedItemSize(itemId);
    }

    /**
     * Returns the number of estimated bytes. This includes the downloaded size and the pending
     * size.
     * @param itemId item. If null, returns the sum from all items.
     * @return
     */
    public long getEstimatedItemSize(String itemId) {
        return mProvider.getEstimatedItemSize(itemId);
    }

    /**
     * Create a new item. Does not start the download and does not retrieve metadata from the network.
     * Use {@link DownloadItem#loadMetadata()} to load metadata and inspect it.
     * @param itemId
     * @param contentURL
     * @return
     */
    public DownloadItem createItem(String itemId, String contentURL) {
        return mProvider.createItem(itemId, contentURL);
    }

    /**
     * Remove item entirely. Deletes all files and db records.
     * @param itemId
     */
    public void removeItem(String itemId) {
        DownloadItem item = findItem(itemId);
        // TODO: can the lower-level methods use itemId and not item?
        mProvider.removeItem(item);
    }

    private File getItemDir(String itemId) {
        // TODO: safe itemId?
        File itemDir = new File(mItemsDir, itemId);
        return itemDir;
    }

    public File getAppDataDir(String itemId) {
        File appDataDir = new File(getItemDir(itemId), "appData");
        appDataDir.mkdirs();
        return appDataDir;
    }

    /**
     * Get list of downloads in a given set of states.
     * @param states
     * @return
     */
    public List<DownloadItem> getDownloads(DownloadState... states) {
        return mProvider.getDownloads(states);
    }

    /**
     * Get playback URL of a given item.
     * @param itemId
     * @return
     */
    public String getPlaybackURL(String itemId) {
        return mProvider.getPlaybackURL(itemId);
    }

    /**
     * Get the File that represents the locally downloaded item.
     * @param itemId
     * @return
     */
    public File getLocalFile(String itemId) {
        return mProvider.getLocalFile(itemId);
    }
}

