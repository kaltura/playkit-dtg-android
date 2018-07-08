package com.kaltura.dtg;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public interface Downloader {
    void parseOriginManifest() throws IOException;

    void setSelectedTracksMap(HashMap<DownloadItem.TrackType, List<BaseTrack>> trackTypeListHashMap);

    void setAvailableTracks(HashMap<DownloadItem.TrackType, List<BaseTrack>> trackTypeListHashMap);

    Map<DownloadItem.TrackType, List<BaseTrack>> getAvailableTracks();

    void setSelectedTracks(DownloadItem.TrackType type, List<BaseTrack> selectedTracks);

    List<BaseTrack> getSelectedTracks(DownloadItem.TrackType type);

    Map<DownloadItem.TrackType,List<BaseTrack>> getSelectedTracksMap();

    void createDownloadTasks() throws IOException;

    LinkedHashSet<DownloadTask> getDownloadTasks();

    long getEstimatedDownloadSize();

    void createLocalManifest() throws IOException;

    AssetFormat getAssetFormat();

    String storedOriginManifestName();

    File getTargetDir();
}
