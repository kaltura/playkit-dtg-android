package com.kaltura.dtg;

import android.support.annotation.Nullable;

import java.io.File;
import java.util.List;

/**
 * Created by noamt on 5/13/15.
 */
public interface DownloadProvider {

    void start(ContentManager.OnStartedListener listener);

    void stop();

    void loadItemMetadata(DownloadItem item);

    DownloadState startDownload(String itemId);

    void pauseDownload(DownloadItem item);

    void resumeDownload(DownloadItem item);

    void removeItem(DownloadItem item);

    DownloadItem findItem(String itemId);

    long getDownloadedItemSize(@Nullable String itemId);

    DownloadItem createItem(String itemId, String contentURL);

    List<? extends DownloadItem> getDownloads(DownloadState[] states);

    String getPlaybackURL(String itemId);

    File getLocalFile(String itemId);

    void dumpState();

    void setDownloadStateListener(DownloadStateListener listener);
    
    long getEstimatedItemSize(@Nullable String itemId);


    void updateItemState(String itemId, DownloadState state);

}

