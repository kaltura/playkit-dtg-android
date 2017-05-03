package com.kaltura.dtg;

import android.content.Context;

import com.kaltura.dtg.clear.ContentManagerImp;

import java.io.File;
import java.util.List;

/**
 * Created by Noam Tamim @ Kaltura on 28/09/2016.
 */
public abstract class ContentManager {
    public static ContentManager getInstance(Context context) {
        return ContentManagerImp.getInstance(context);
    }

    /**
     * Add download listener.
     * @param listener
     */
    public abstract void addDownloadStateListener(DownloadStateListener listener);

    /**
     * Remove download listener.
     * @param listener
     */
    public abstract void removeDownloadStateListener(DownloadStateListener listener);

    /**
     * Set the maximum number of concurrent downloads. Must be called before {@link #start(OnStartedListener)}
     * and cannot be changed after that.
     * @param maxConcurrentDownloads
     */
    public abstract void setMaxConcurrentDownloads(int maxConcurrentDownloads);
    
    /**
     * Stop the downloader. Stops all running downloads, but keep them in IN_PROGRESS state.
     */
    public abstract void stop();

    /**
     * Start the download manager. Starts all downloads that were in IN_PROGRESS state when the 
     * manager was stopped. Add listeners before calling this method.
     */
    public abstract void start(OnStartedListener onStartedListener);

    /**
     * Pause all downloads (set their state to PAUSE and stop downloading).
     */
    public abstract void pauseDownloads();

    /**
     * Resume all PAUSED downloads.
     */
    public abstract void resumeDownloads();

    /**
     * Find and return an item.
     *
     * @return An item identified by itemId, or null if not found.
     */
    public abstract DownloadItem findItem(String itemId);

    /**
     * Returns the number of downloaded bytes. 
     * @param itemId item. If null, returns the sum from all items.
     * @return
     */
    public abstract long getDownloadedItemSize(String itemId);

    /**
     * Returns the number of estimated bytes. This includes the downloaded size and the pending
     * size.
     * @param itemId item. If null, returns the sum from all items.
     * @return
     */
    public abstract long getEstimatedItemSize(String itemId);

    /**
     * Create a new item. Does not start the download and does not retrieve metadata from the network.
     * Use {@link DownloadItem#loadMetadata()} to load metadata and inspect it.
     * @param itemId
     * @param contentURL
     * @return
     */
    public abstract DownloadItem createItem(String itemId, String contentURL);

    /**
     * Remove item entirely. Deletes all files and db records.
     * @param itemId
     */
    public abstract void removeItem(String itemId);

    public abstract File getAppDataDir(String itemId);

    /**
     * Get list of downloads in a given set of states.
     * @param states
     * @return
     */
    public abstract List<DownloadItem> getDownloads(DownloadState... states);

    /**
     * Get playback URL of a given item.
     * @param itemId
     * @return
     */
    public abstract String getPlaybackURL(String itemId);

    /**
     * Get the File that represents the locally downloaded item.
     * @param itemId
     * @return
     */
    public abstract File getLocalFile(String itemId);

    public interface OnStartedListener {
        void onStarted();
    }
}
