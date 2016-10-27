package com.kaltura.dtg.clear;

import android.support.annotation.NonNull;

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

    private ClearDownloadItem mItem;

    private Map<DownloadItem.TrackType, List<DashTrack>> mOriginalSelectedTracks;
    private boolean mTrackSelectionChanged;


    DashDownloadUpdater(ClearDownloadItem item) throws IOException {
        super(item.getContentURL(), new File(item.getDataDir()));
        mItem = item;

        loadOriginManifest();
        parseOriginManifest();

        mSelectedTracks = new HashMap<>();
        mAvailableTracks = new HashMap<>();
        mOriginalSelectedTracks = new HashMap<>();
        
        for (DownloadItem.TrackType type : DownloadItem.TrackType.values()) {
            List<DashTrack> availableTracks = mItem.getProvider().readTracksFromDB(item.getItemId(), type, null);
            mAvailableTracks.put(type, availableTracks);

            List<DashTrack> selectedTracks = mItem.getProvider().readTracksFromDB(item.getItemId(), type, TrackState.SELECTED);
            mSelectedTracks.put(type, selectedTracks);
            mOriginalSelectedTracks.put(type, selectedTracks);
        }
    }

    @Override
    void setSelectedTracks(@NonNull DownloadItem.TrackType type, @NonNull List<DashTrack> tracks) {
        mTrackSelectionChanged = true;
        super.setSelectedTracks(type, tracks);
    }

    ClearDownloadItem getItem() {
        return mItem;
    }

    void apply() throws IOException {
        // Update Track table
        if (!mTrackSelectionChanged) {
            // No change
            return;
        }

        Map<DownloadItem.TrackType, List<DashTrack>> tracksToUnselect = new HashMap<>();
        for (DownloadItem.TrackType trackType : DownloadItem.TrackType.values()) {
            List<DashTrack> unselect = new ArrayList<>();
            for (DashTrack dashTrack : mOriginalSelectedTracks.get(trackType)) {
                if (! mSelectedTracks.get(trackType).contains(dashTrack)) {
                    unselect.add(dashTrack);
                }
            }
            
            tracksToUnselect.put(trackType, unselect);
        }
        
        mItem.getProvider().updateTracksInDB(mItem.getItemId(), tracksToUnselect, TrackState.NOT_SELECTED);
        mItem.getProvider().updateTracksInDB(mItem.getItemId(), mSelectedTracks, TrackState.SELECTED);

        // Add DownloadTasks
        createDownloadTasks();
        mItem.getProvider().addDownloadTasksToDB(mItem, new ArrayList<>(mDownloadTasks));
        
        // Update item size
        mItem.setEstimatedSizeBytes(getEstimatedDownloadSize());
        mItem.getProvider().updateItemInfoInDB(mItem, Database.COL_ITEM_ESTIMATED_SIZE);
        

        // Update localized manifest
        createLocalManifest();
        
        mItem.setTrackSelector(null);
    }

    private void loadOriginManifest() throws IOException {
        FileInputStream inputStream = new FileInputStream(new File(mItem.getDataDir(), ORIGIN_MANIFEST_MPD));
        mOriginManifestBytes = Utils.fullyReadInputStream(inputStream, MAX_DASH_MANIFEST_SIZE).toByteArray();
    }
    
    List<DashTrack> getDownloadedTracks(@NonNull DownloadItem.TrackType type) {

        List<DashTrack> downloadedTracks = new ArrayList<>();
        
        for (DashTrack dashTrack : mSelectedTracks.get(type)) {
            
            if (mItem.getProvider().countPendingFiles(mItem.getItemId(), dashTrack.getRelativeId()) == 0) {
                downloadedTracks.add(dashTrack);
            }
        }
        
        return downloadedTracks;
    }
}
