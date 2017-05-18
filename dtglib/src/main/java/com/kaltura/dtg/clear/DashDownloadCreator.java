package com.kaltura.dtg.clear;

import android.util.Log;

import com.kaltura.android.exoplayer.chunk.Format;
import com.kaltura.android.exoplayer.dash.mpd.AdaptationSet;
import com.kaltura.android.exoplayer.dash.mpd.Representation;
import com.kaltura.dtg.DownloadItem;
import com.kaltura.dtg.Utils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

/**
 * Created by noamt on 13/09/2016.
 */
class DashDownloadCreator extends DashDownloader {

    private static final String TAG = "DashDownloadCreator";
    private boolean applied;

    DashDownloadCreator(String manifestUrl, File targetDir) throws IOException {
        super(manifestUrl, targetDir);

        downloadManifest();
        parseOriginManifest();
        createTracks();

        selectDefaultTracks();
    }

    @Override
    List<DashTrack> getDownloadedTracks(DownloadItem.TrackType type) {
        Log.w(TAG, "Initial selector has no downloaded tracks!");
        return null;
    }

    private void selectDefaultTracks() {

        selectedTracks = new HashMap<>();
        for (DownloadItem.TrackType type : DownloadItem.TrackType.values()) {
            selectedTracks.put(type, new ArrayList<DashTrack>(1));
        }


        // "Best" == highest bitrate.
        
        // Video: simply select best track.
        List<DashTrack> availableVideoTracks = getAvailableTracks(DownloadItem.TrackType.VIDEO);
        if (availableVideoTracks.size() > 0) {
            DashTrack selectedVideoTrack = Collections.max(availableVideoTracks, DownloadItem.Track.bitrateComparator);
            setSelectedTracks(DownloadItem.TrackType.VIDEO, Collections.singletonList(selectedVideoTrack));
        }

        // Audio: X=(language of first track); Select best track with language==X.
        List<DashTrack> availableAudioTracks = getAvailableTracks(DownloadItem.TrackType.AUDIO);
        if (availableAudioTracks.size() > 0) {
            String firstAudioLang = availableAudioTracks.get(0).getLanguage();
            List<DashTrack> tracks;
            if (firstAudioLang != null) {
                tracks = DashTrack.filterByLanguage(firstAudioLang, availableAudioTracks);
            } else {
                tracks = availableAudioTracks;
            }
            
            DashTrack selectedAudioTrack = Collections.max(tracks, DownloadItem.Track.bitrateComparator);
            setSelectedTracks(DownloadItem.TrackType.AUDIO, Collections.singletonList(selectedAudioTrack));
        }

        // Text: simply select first track.
        List<DashTrack> availableTextTracks = getAvailableTracks(DownloadItem.TrackType.TEXT);
        if (availableTextTracks.size() > 0) {
            DashTrack selectedTextTrack = availableTextTracks.get(0);
            setSelectedTracks(DownloadItem.TrackType.TEXT, Collections.singletonList(selectedTextTrack));
        }
    }

    void apply() throws IOException {
        if (applied) {
            Log.w(TAG, "Ignoring unsupported extra call to apply()");
        } else {
            createLocalManifest();
            createDownloadTasks();
            applied = true;
        }
    }

    private void downloadManifest() throws IOException {
        URL url = new URL(manifestUrl);
        File targetFile = new File(targetDir, ORIGIN_MANIFEST_MPD);
        originManifestBytes = Utils.downloadToFile(url, targetFile, MAX_DASH_MANIFEST_SIZE);
    }

    private void createTracks() {

        List<AdaptationSet> adaptationSets = currentPeriod.adaptationSets;

        availableTracks = new HashMap<>();
        for (DownloadItem.TrackType type : DownloadItem.TrackType.values()) {
            availableTracks.put(type, new ArrayList<DashTrack>(1));
        }
        
        ListIterator<AdaptationSet> itAdaptations = adaptationSets.listIterator();
        while (itAdaptations.hasNext()) {
            int adaptationIndex = itAdaptations.nextIndex();
            AdaptationSet adaptationSet = itAdaptations.next();

            ListIterator<Representation> itRepresentations = adaptationSet.representations.listIterator();
            while (itRepresentations.hasNext()) {
                int representationIndex = itRepresentations.nextIndex();
                Representation representation = itRepresentations.next();
                DownloadItem.TrackType type;
                switch (adaptationSet.type) {
                    case AdaptationSet.TYPE_VIDEO:
                        type = DownloadItem.TrackType.VIDEO;
                        break;
                    case AdaptationSet.TYPE_AUDIO:
                        type = DownloadItem.TrackType.AUDIO;
                        break;
                    case AdaptationSet.TYPE_TEXT:
                        type = DownloadItem.TrackType.TEXT;
                        break;
                    default:
                        type = DownloadItem.TrackType.UNKNOWN;
                        break;
                }
                if (type != DownloadItem.TrackType.UNKNOWN) {
                    Format format = representation.format;
                    DashTrack track = new DashTrack(type, format.language, format.bitrate, adaptationIndex, representationIndex);
                    track.setHeight(format.height);
                    track.setWidth(format.width);
                    availableTracks.get(type).add(track);
                }
            }
        }
    }
}
