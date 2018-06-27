package com.kaltura.dtg.dash;

import android.support.annotation.NonNull;

import com.kaltura.dtg.BaseTrack;
import com.kaltura.dtg.DownloadItemImp;
import com.kaltura.dtg.DownloadItem;
import com.kaltura.dtg.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by noamt on 13/09/2016.
 */
class DashDownloadUpdater extends DashDownloader {

    private static final String TAG = "DashDownloadCreator";

    private DownloadItemImp item;

    private Map<DownloadItem.TrackType, List<BaseTrack>> originalSelectedTracks;
    private boolean trackSelectionChanged;


    DashDownloadUpdater(DownloadItemImp item) throws IOException {
        super(item.getContentURL(), new File(item.getDataDir()));
        this.item = item;

        loadOriginManifest();
        parseOriginManifest();

        selectedTracks = new HashMap<>();
        availableTracks = new HashMap<>();
        originalSelectedTracks = new HashMap<>();
        
        for (DownloadItem.TrackType type : DownloadItem.TrackType.values()) {
            List<BaseTrack> availableTracks = this.item.getService().readTracksFromDB(item.getItemId(), type, null);
            this.availableTracks.put(type, availableTracks);

            List<BaseTrack> selectedTracks = this.item.getService().readTracksFromDB(item.getItemId(), type, BaseTrack.TrackState.SELECTED);
            this.selectedTracks.put(type, selectedTracks);
            originalSelectedTracks.put(type, selectedTracks);
        }
    }

    @Override
    void setSelectedTracks(@NonNull DownloadItem.TrackType type, @NonNull List<BaseTrack> tracks) {
        trackSelectionChanged = true;
        super.setSelectedTracks(type, tracks);
    }

    DownloadItemImp getItem() {
        return item;
    }

    void apply() throws IOException {
        // Update Track table
        if (!trackSelectionChanged) {
            // No change
            return;
        }

        Map<DownloadItem.TrackType, List<BaseTrack>> tracksToUnselect = new HashMap<>();
        for (DownloadItem.TrackType trackType : DownloadItem.TrackType.values()) {
            List<BaseTrack> unselect = new ArrayList<>();
            for (BaseTrack dashTrack : originalSelectedTracks.get(trackType)) {
                if (! selectedTracks.get(trackType).contains(dashTrack)) {
                    unselect.add(dashTrack);
                }
            }
            
            tracksToUnselect.put(trackType, unselect);
        }
        
        item.getService().updateTracksInDB(item.getItemId(), tracksToUnselect, BaseTrack.TrackState.NOT_SELECTED);
        item.getService().updateTracksInDB(item.getItemId(), selectedTracks, BaseTrack.TrackState.SELECTED);

        // Add DownloadTasks
        createDownloadTasks();
        item.getService().addDownloadTasksToDB(item, new ArrayList<>(downloadTasks));
        
        // Update item size
        item.setEstimatedSizeBytes(getEstimatedDownloadSize());
        item.getService().updateItemEstimatedSizeInDB(item);

        // Update localized manifest
        createLocalManifest();
        
        item.setTrackSelector(null);
    }

    private void loadOriginManifest() throws IOException {
        FileInputStream inputStream = new FileInputStream(new File(item.getDataDir(), ORIGIN_MANIFEST_MPD));
        originManifestBytes = Utils.fullyReadInputStream(inputStream, MAX_DASH_MANIFEST_SIZE).toByteArray();
    }
    
    List<BaseTrack> getDownloadedTracks(@NonNull DownloadItem.TrackType type) {

        List<BaseTrack> downloadedTracks = new ArrayList<>();
        
        for (BaseTrack dashTrack : selectedTracks.get(type)) {
            
            if (item.getService().countPendingFiles(item.getItemId(), dashTrack) == 0) {
                downloadedTracks.add(dashTrack);
            }
        }
        
        return downloadedTracks;
    }
}
