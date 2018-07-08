package com.kaltura.dtg;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class DownloadUpdater {
    private final BaseAbrDownloader downloader;
    private DownloadItemImp item;

    private Map<DownloadItem.TrackType, List<BaseTrack>> originalSelectedTracks;
    public boolean trackSelectionChanged;


    public DownloadUpdater(DownloadItemImp item, BaseAbrDownloader downloader) throws IOException {
        this.item = item;
        this.downloader = downloader;

//        downloader.parseOriginManifest();
//
//        downloader.setSelectedTracksMap(new HashMap<DownloadItem.TrackType, List<BaseTrack>>());
//        downloader.setAvailableTracksMap(new HashMap<DownloadItem.TrackType, List<BaseTrack>>());
//        originalSelectedTracks = new HashMap<>();
//
//        for (DownloadItem.TrackType type : DownloadItem.TrackType.valid) {
//            List<BaseTrack> availableTracks = this.item.getService().readTracksFromDB(item.getItemId(), type, null, downloader.getAssetFormat());
//            downloader.getAvailableTracks().put(type, availableTracks);
//
//            List<BaseTrack> selectedTracks = this.item.getService().readTracksFromDB(item.getItemId(), type, BaseTrack.TrackState.SELECTED, downloader.getAssetFormat());
//            downloader.setSelectedTracks(type, selectedTracks);
//            originalSelectedTracks.put(type, selectedTracks);
//        }
    }

//    public void apply() throws IOException {
//        // Update Track table
//        if (!trackSelectionChanged) {
//            // No change
//            return;
//        }
//
//        Map<DownloadItem.TrackType, List<BaseTrack>> tracksToUnselect = new HashMap<>();
//        for (DownloadItem.TrackType trackType : DownloadItem.TrackType.values()) {
//            List<BaseTrack> unselect = new ArrayList<>();
//            for (BaseTrack track : originalSelectedTracks.get(trackType)) {
//                if (!downloader.getSelectedTracks(trackType).contains(track)) {
//                    unselect.add(track);
//                }
//            }
//
//            tracksToUnselect.put(trackType, unselect);
//        }
//
//        item.getService().updateTracksInDB(item.getItemId(), tracksToUnselect, BaseTrack.TrackState.NOT_SELECTED);
//        item.getService().updateTracksInDB(item.getItemId(), downloader.getSelectedTracksMap(), BaseTrack.TrackState.SELECTED);
//
//        // Add DownloadTasks
//        downloader.createDownloadTasks();
//        item.getService().addDownloadTasksToDB(item, new ArrayList<>(downloader.getDownloadTasks()));
//
//        // Update item size
//        item.setEstimatedSizeBytes(downloader.getEstimatedDownloadSize());
//        item.getService().updateItemEstimatedSizeInDB(item);
//
//        // Update localized manifest
//        downloader.createLocalManifest();
//
//        item.setTrackSelector(null);
//    }

}
