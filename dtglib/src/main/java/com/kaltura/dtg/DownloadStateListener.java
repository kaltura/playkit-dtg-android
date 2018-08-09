package com.kaltura.dtg;

public interface DownloadStateListener {
    void onDownloadComplete(DownloadItem item);

    void onProgressChange(DownloadItem item, long downloadedBytes);

    void onDownloadStart(DownloadItem item);

    void onDownloadPause(DownloadItem item);

    void onDownloadFailure(DownloadItem item, Exception error);

    void onDownloadMetadata(DownloadItem item, Exception error);

    void onTracksAvailable(DownloadItem item, DownloadItem.TrackSelector trackSelector);
}

