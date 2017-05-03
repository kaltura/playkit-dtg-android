package com.kaltura.dtg;

/**
 * Created by Aviran Abady on 7/2/15.
 */
public interface DownloadStateListener {
    void onDownloadComplete(DownloadItem item);

    void onProgressChange(DownloadItem item, long downloadedBytes);

    void onDownloadStart(DownloadItem item);

    void onDownloadPause(DownloadItem item);

    void onDownloadFailure(DownloadItem item);
    
    void onDownloadMetadata(DownloadItem item, Exception error);

    /**
     * Allow application to modify the default track selection.
     * @param item
     */
    void onTracksAvailable(DownloadItem item, DownloadItem.TrackSelector trackSelector);
    
    DownloadStateListener noopListener = new DownloadStateListener() {
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
        public void onDownloadFailure(DownloadItem item) {

        }

        @Override
        public void onDownloadMetadata(DownloadItem item, Exception error) {
            
        }

        @Override
        public void onTracksAvailable(DownloadItem item, DownloadItem.TrackSelector trackSelector) {
            
        }
    };
}

