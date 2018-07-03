package com.kaltura.dtg.hls;

import android.util.Log;

import com.kaltura.dtg.BaseTrack;
import com.kaltura.dtg.DownloadItem;
import com.kaltura.dtg.Utils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class HlsDownloadCreator extends HlsDownloader {
    private boolean applied;
    private static final String TAG = "HlsDownloadCreator";

    HlsDownloadCreator(String contentURL, File itemDataDir) throws IOException {
        super(contentURL, itemDataDir);

        downloadMasterPlaylist();
        parseOriginManifest();
        createTracks();

        selectDefaultTracks();
    }

    private void createTracks() {

        Map<DownloadItem.TrackType, List<BaseTrack>> availableTracks = new HashMap<>();

        for (DownloadItem.TrackType trackType : DownloadItem.TrackType.valid) {
            availableTracks.put(trackType, new ArrayList<BaseTrack>());
        }

        addTracks(availableTracks, hlsAsset.videoTracks, DownloadItem.TrackType.VIDEO);
        addTracks(availableTracks, hlsAsset.audioTracks, DownloadItem.TrackType.AUDIO);
        addTracks(availableTracks, hlsAsset.textTracks, DownloadItem.TrackType.TEXT);

        setAvailableTracks(availableTracks);
    }

    private void addTracks(Map<DownloadItem.TrackType, List<BaseTrack>> trackMap, List<HlsAsset.Track> tracks, DownloadItem.TrackType trackType) {
        for (HlsAsset.Track track : tracks) {
            trackMap.get(trackType).add(track);
        }
    }

    @Override
    protected List<BaseTrack> getDownloadedTracks(DownloadItem.TrackType type) {
        return null;
    }

    @Override
    protected void apply() throws IOException {
        if (applied) {
            Log.w(TAG, "Ignoring unsupported extra call to apply()");
            return;
        }

        // Download
        for (BaseTrack bt : getSelectedTracksFlat()) {
            final HlsAsset.Track track = (HlsAsset.Track) bt;
            final File trackTargetDir = getTrackTargetDir(track);
            trackTargetDir.mkdirs();// FIXME: 03/07/2018 check
            final File targetFile = new File(trackTargetDir, ORIGIN_M3U8);
            final byte[] bytes = Utils.downloadToFile(new URL(track.url), targetFile, MAX_PLAYLIST_SIZE);
            track.parse(bytes);
        }

        createLocalManifest();
        createDownloadTasks();
        applied = true;

    }
}
