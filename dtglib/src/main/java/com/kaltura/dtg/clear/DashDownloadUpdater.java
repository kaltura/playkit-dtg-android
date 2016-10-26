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

/**
 * Created by noamt on 13/09/2016.
 */
class DashDownloadUpdater extends DashDownloader {

    private static final String TAG = "DashDownloadCreator";

    private ClearDownloadItem mItem;


    DashDownloadUpdater(ClearDownloadItem item) throws IOException {
        super(item.getContentURL(), new File(item.getDataDir()));
        mItem = item;

        loadOriginManifest();
        parseOriginManifest();

        mSelectedTracks = new HashMap<>();
        mAvailableTracks = new HashMap<>();
        
        for (DownloadItem.TrackType type : DownloadItem.TrackType.values()) {
            List<DashTrack> availableTracks = mItem.getProvider().readTracksFromDB(item.getItemId(), type, null);
            mAvailableTracks.put(type, availableTracks);

            List<DashTrack> selectedTracks = mItem.getProvider().readTracksFromDB(item.getItemId(), type, TrackState.SELECTED);
            mSelectedTracks.put(type, selectedTracks);
        }

    }
    
    ClearDownloadItem getItem() {
        return mItem;
    }

    void apply() throws IOException {
        // Update Track table
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
