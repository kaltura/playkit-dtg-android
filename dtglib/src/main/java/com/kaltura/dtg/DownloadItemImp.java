package com.kaltura.dtg;

import android.net.Uri;
import android.util.Log;

import java.util.Date;

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
    private long durationMS;

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

    void setService(DownloadService provider) {
        this.service = provider;
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
        service.startDownload(this);
    }

    @Override
    public long getDurationMS() {
        return durationMS;
    }

    public void setDurationMS(long duration) {
        this.durationMS = duration;
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

        if (playbackPath == null || !(playbackPath.endsWith(AssetFormat.dash.extension()) || playbackPath.endsWith(AssetFormat.hls.extension()))) {
            Log.w(TAG, "Track selection is only supported for dash/hls");
            return null;
        }

        // If selection is in progress, return the current selector.
        if (trackSelector == null) {
            trackSelector = AbrDownloader.newTrackUpdater(this, service.settings);
        }

        return trackSelector;
    }

    public void setTrackSelector(TrackSelector trackSelector) {
        this.trackSelector = trackSelector;
    }

    @Override
    public AssetFormat getAssetFormat() {
        if (playbackPath != null) {
            final AssetFormat format = AssetFormat.byFilename(playbackPath);
            if (format != null) {
                return format;
            }
        }

        if (contentUrl != null) {
            final AssetFormat format = AssetFormat.byFilename(Uri.parse(contentUrl).getLastPathSegment());
            if (format != null) {
                return format;
            }
        }

        return null;
    }

    DownloadService getService() {
        return service;
    }
}
