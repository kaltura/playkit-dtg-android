package com.kaltura.dtg;

import android.util.Log;

import java.io.IOException;
import java.util.Date;

/**
 * Created by noamt on 30/05/2016.
 */
public class DownloadItemImp implements DownloadItem {

    private static final String TAG = "DownloadItemImp";
    private final String itemId;
    private final String contentUrl;

    private DownloadService service;
    private DownloadState state = DownloadState.NEW;
    private long addedTime;
    private long finishedTime;
    private long estimatedSizeBytes;
    private long downloadedSizeBytes;
    
    private String dataDir;
    private String playbackPath;
    
    private TrackSelector trackSelector;

    DownloadItemImp(String itemId, String contentURL) {
        this.itemId = itemId;
        this.contentUrl = contentURL;

    }

    @Override
    public String toString() {
        return "<" + getClass().getName() + " itemId=" + getItemId() + " contentUrl=" + getContentURL() +
                " state=" + state.name() + " addedTime=" + new Date(addedTime) +
                " estimatedSizeBytes=" + estimatedSizeBytes +
                " downloadedSizeBytes=" + downloadedSizeBytes + ">";
    }

    @Override
    public String getItemId() {
        return itemId;
    }

    @Override
    public String getContentURL() {
        return contentUrl;
    }

    public String getDataDir() {
        return dataDir;
    }

    void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    String getPlaybackPath() {
        return playbackPath;
    }

    public void setPlaybackPath(String playbackPath) {
        this.playbackPath = playbackPath;
    }
    
    void setProvider(DownloadService provider) {
        this.service = provider;
    }

    void updateItemState(DownloadState state) {
        service.updateItemState(this.getItemId(), state);
    }

    void setFinishedTime(long finishedTime) {
        this.finishedTime = finishedTime;
    }

    long incDownloadBytes(long downloadedBytes) {
        long updated = downloadedSizeBytes + downloadedBytes;
        this.downloadedSizeBytes = updated;
        return updated;
    }

    @Override
    public void startDownload() {
        service.startDownload(this.getItemId());
//        this.setState(state);
    }
    
    @Override
    public long getEstimatedSizeBytes() {
        return estimatedSizeBytes;
    }
    
    public void setEstimatedSizeBytes(long bytes) {
        estimatedSizeBytes = bytes;
    }

    @Override
    public long getDownloadedSizeBytes() {
        return downloadedSizeBytes;
    }
    
    void setDownloadedSizeBytes(long bytes) {
        downloadedSizeBytes = bytes;
    }

    @Override
    public DownloadState getState() {
        return state;
    }

    void setState(DownloadState state) {
        this.state = state;
    }

    @Override
    public void loadMetadata() {
        service.loadItemMetadata(this);
    }

    @Override
    public void pauseDownload() {
        service.pauseDownload(this);
    }

    @Override
    public long getAddedTime() {
        return addedTime;
    }

    void setAddedTime(long addedTime) {
        this.addedTime = addedTime;
    }

    @Override
    public TrackSelector getTrackSelector() {
        
        if (playbackPath ==null || !playbackPath.endsWith(".mpd")) {
            Log.w(TAG, "Track selection is only supported for dash");
            return null;
        }
        
        // If selection is in progress, return the current selector.
        
        if (trackSelector == null) {
            BaseAbrDownloader updater;
            try {
                updater = BaseAbrDownloader.createUpdater(this);
            } catch (IOException e) {
                Log.e(TAG, "Error initializing updater", e);
                return null;
            }
            TrackSelector trackSelector = updater.getTrackSelector();

            setTrackSelector(trackSelector);
        }

        return trackSelector;
    }

    public void setTrackSelector(TrackSelector trackSelector) {
        this.trackSelector = trackSelector;
    }

    public DownloadService getService() {
        return service;
    }
}
