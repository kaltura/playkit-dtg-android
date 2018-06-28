package com.kaltura.dtg.dash;

import android.support.annotation.NonNull;

import com.kaltura.dtg.AssetFormat;
import com.kaltura.dtg.BaseTrack;
import com.kaltura.dtg.DownloadItem.TrackType;
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

    private Map<TrackType, List<BaseTrack>> originalSelectedTracks;
    private boolean trackSelectionChanged;


    DashDownloadUpdater(DownloadItemImp item) throws IOException {
        super(item.getContentURL(), new File(item.getDataDir()));
        this.item = item;

        loadOriginManifest();
        parseOriginManifest();

        setSelectedTracksMap(new HashMap<TrackType, List<BaseTrack>>());
        setAvailableTracks(new HashMap<TrackType, List<BaseTrack>>());
        originalSelectedTracks = new HashMap<>();
        
        for (TrackType type : TrackType.values()) {
            List<BaseTrack> availableTracks = this.item.getService().readTracksFromDB(item.getItemId(), type, null, AssetFormat.dash);
            this.getAvailableTracks().put(type, availableTracks);

            List<BaseTrack> selectedTracks = this.item.getService().readTracksFromDB(item.getItemId(), type, BaseTrack.TrackState.SELECTED, AssetFormat.dash);
            this.setSelectedTracks(type, selectedTracks);
            originalSelectedTracks.put(type, selectedTracks);
        }
    }

    @Override
    protected void setSelectedTracks(@NonNull TrackType type, @NonNull List<BaseTrack> tracks) {
        trackSelectionChanged = true;
        super.setSelectedTracks(type, tracks);
    }

    DownloadItemImp getItem() {
        return item;
    }

    protected void apply() throws IOException {
        // Update Track table
        if (!trackSelectionChanged) {
            // No change
            return;
        }

        Map<TrackType, List<BaseTrack>> tracksToUnselect = new HashMap<>();
        for (TrackType trackType : TrackType.values()) {
            List<BaseTrack> unselect = new ArrayList<>();
            for (BaseTrack dashTrack : originalSelectedTracks.get(trackType)) {
                if (!getSelectedTracks(trackType).contains(dashTrack)) {
                    unselect.add(dashTrack);
                }
            }
            
            tracksToUnselect.put(trackType, unselect);
        }
        
        item.getService().updateTracksInDB(item.getItemId(), tracksToUnselect, BaseTrack.TrackState.NOT_SELECTED);
        item.getService().updateTracksInDB(item.getItemId(), getSelectedTracksMap(), BaseTrack.TrackState.SELECTED);

        // Add DownloadTasks
        createDownloadTasks();
        item.getService().addDownloadTasksToDB(item, new ArrayList<>(getDownloadTasks()));
        
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
    
    protected List<BaseTrack> getDownloadedTracks(@NonNull TrackType type) {

        List<BaseTrack> downloadedTracks = new ArrayList<>();
        
        for (BaseTrack dashTrack : getSelectedTracks(type)) {
            if (item.getService().countPendingFiles(item.getItemId(), dashTrack) == 0) {
                downloadedTracks.add(dashTrack);
            }
        }
        
        return downloadedTracks;
    }
}
