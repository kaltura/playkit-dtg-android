package com.kaltura.dtg.dash;

import android.util.Log;

import com.kaltura.android.exoplayer.chunk.Format;
import com.kaltura.android.exoplayer.dash.mpd.AdaptationSet;
import com.kaltura.android.exoplayer.dash.mpd.Representation;
import com.kaltura.dtg.BaseTrack;
import com.kaltura.dtg.DownloadItem.TrackType;
import com.kaltura.dtg.Utils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
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
    protected List<BaseTrack> getDownloadedTracks(TrackType type) {
        Log.w(TAG, "Initial selector has no downloaded tracks!");
        return null;
    }

    protected void apply() throws IOException {
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
        File targetFile = new File(getTargetDir(), ORIGIN_MANIFEST_MPD);
        originManifestBytes = Utils.downloadToFile(url, targetFile, MAX_DASH_MANIFEST_SIZE);
    }

    private void createTracks() {

        List<AdaptationSet> adaptationSets = currentPeriod.adaptationSets;

        setAvailableTracks(new HashMap<TrackType, List<BaseTrack>>());
        for (TrackType type : TrackType.valid) {
            setAvailableTracks(type, new ArrayList<BaseTrack>(1));
        }
        
        ListIterator<AdaptationSet> itAdaptations = adaptationSets.listIterator();
        while (itAdaptations.hasNext()) {
            int adaptationIndex = itAdaptations.nextIndex();
            AdaptationSet adaptationSet = itAdaptations.next();

            ListIterator<Representation> itRepresentations = adaptationSet.representations.listIterator();
            while (itRepresentations.hasNext()) {
                int representationIndex = itRepresentations.nextIndex();
                Representation representation = itRepresentations.next();
                TrackType type;
                switch (adaptationSet.type) {
                    case AdaptationSet.TYPE_VIDEO:
                        type = TrackType.VIDEO;
                        break;
                    case AdaptationSet.TYPE_AUDIO:
                        type = TrackType.AUDIO;
                        break;
                    case AdaptationSet.TYPE_TEXT:
                        type = TrackType.TEXT;
                        break;
                    default:
                        type = TrackType.UNKNOWN;
                        break;
                }
                if (type != TrackType.UNKNOWN) {
                    Format format = representation.format;
                    DashTrack track = new DashTrack(type, format, adaptationIndex, representationIndex);
                    track.setHeight(format.height);
                    track.setWidth(format.width);
                    getAvailableTracks().get(type).add(track);
                }
            }
        }
    }

}
