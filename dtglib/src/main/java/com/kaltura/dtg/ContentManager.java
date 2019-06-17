package com.kaltura.dtg;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.List;

public abstract class ContentManager {
    private static final String VERSION_STRING = BuildConfig.VERSION_NAME;
    static final String CLIENT_TAG = "playkit-dtg/android-" + VERSION_STRING;

    public static ContentManager getInstance(Context context) {
        return ContentManagerImp.getInstance(context);
    }

    /**
     * Add download listener.
     *
     * @param listener
     */
    public abstract void addDownloadStateListener(DownloadStateListener listener);

    /**
     * Remove download listener.
     *
     * @param listener
     */
    public abstract void removeDownloadStateListener(DownloadStateListener listener);

    /**
     * Auto start items marked as {@link DownloadState#IN_PROGRESS}. Default is true.
     * This setter only has effect if called before {@link #start(OnStartedListener)}.
     *
     * @param autoStartItemsInProgress
     */
    public abstract void setAutoResumeItemsInProgress(boolean autoStartItemsInProgress);

    /**
     * Start the download manager. Starts all downloads that were in IN_PROGRESS state when the
     * manager was stopped. Add listeners before calling this method.
     * @throws IOException if an error has occurred when trying to prepare the storage.
     */
    public abstract void start(OnStartedListener onStartedListener) throws IOException;

    /**
     * Stop the downloader. Stops all running downloads, but keep them in IN_PROGRESS state.
     */
    public abstract void stop();

    /**
     * Pause all downloads (set their state to PAUSE and stop downloading).
     */
    public abstract void pauseDownloads() throws IllegalStateException;

    /**
     * Resume all PAUSED downloads.
     */
    public abstract void resumeDownloads() throws IllegalStateException;

    /**
     * Find and return an item.
     *
     * @return An item identified by itemId, or null if not found.
     */
    public abstract DownloadItem findItem(String itemId) throws IllegalStateException;

    /**
     * Returns the number of downloaded bytes.
     *
     * @param itemId item. If null, returns the sum from all items.
     * @return
     */
    public abstract long getDownloadedItemSize(String itemId) throws IllegalStateException;

    /**
     * Returns the number of estimated bytes. This includes the downloaded size and the pending
     * size.
     *
     * @param itemId item. If null, returns the sum from all items.
     * @return
     */
    public abstract long getEstimatedItemSize(String itemId) throws IllegalStateException;

    /**
     * Create a new item. Does not start the download and does not retrieve metadata from the network.
     * Use {@link DownloadItem#loadMetadata()} to load metadata and inspect it.
     *
     * @param itemId
     * @param contentURL
     * @return
     */
    public abstract DownloadItem createItem(String itemId, String contentURL) throws IllegalStateException, IOException;

    /**
     * Remove item entirely. Deletes all files and db records.
     *
     * @param itemId
     */
    public abstract void removeItem(String itemId) throws IllegalStateException;

    public abstract File getAppDataDir(String itemId);

    /**
     * Get list of downloads in a given set of states.
     *
     * @param states
     * @return
     */
    public abstract List<DownloadItem> getDownloads(DownloadState... states) throws IllegalStateException;

    /**
     * Get playback URL of a given item.
     *
     * @param itemId
     * @return
     */
    public abstract String getPlaybackURL(String itemId) throws IllegalStateException;

    /**
     * Get the File that represents the locally downloaded item.
     *
     * @param itemId
     * @return
     */
    public abstract File getLocalFile(String itemId);

    public abstract boolean isStarted();

    public abstract Settings getSettings();

    public interface OnStartedListener {
        void onStarted();
    }

    public static class Settings implements Cloneable {
        public int maxDownloadRetries = 5;
        public int httpTimeoutMillis = 15000;
        public int maxConcurrentDownloads = 4;
        public String applicationName = "";
        public boolean createNoMediaFileInDownloadsDir = true;
        public int defaultHlsAudioBitrateEstimation = 64000;
        public long freeDiskSpaceRequiredBytes = 400 * 1024 * 1024; // default 400MB

        Settings copy() {
            try {
                return (Settings) clone();
            } catch (CloneNotSupportedException e) {
                return null;
            }
        }
    }
}
