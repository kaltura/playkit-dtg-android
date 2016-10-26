package com.kaltura.dtg.clear;

import android.support.annotation.NonNull;
import android.util.Base64;
import android.util.Log;

import com.kaltura.android.exoplayer.dash.mpd.AdaptationSet;
import com.kaltura.android.exoplayer.dash.mpd.MediaPresentationDescription;
import com.kaltura.android.exoplayer.dash.mpd.MediaPresentationDescriptionParser;
import com.kaltura.android.exoplayer.dash.mpd.Period;
import com.kaltura.android.exoplayer.dash.mpd.RangedUri;
import com.kaltura.android.exoplayer.dash.mpd.Representation;
import com.kaltura.dtg.AppBuildConfig;
import com.kaltura.dtg.DownloadItem;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Created by noamt on 19/06/2016.
 */
abstract class DashDownloader {

    private static final String TAG = "DashDownloader";
    static final String ORIGIN_MANIFEST_MPD = "origin.mpd";
    static final String LOCAL_MANIFEST_MPD = "local.mpd";
    static final int MAX_DASH_MANIFEST_SIZE = 10 * 1024 * 1024;

    enum TrackState {
        NOT_SELECTED, SELECTED, DOWNLOADED,
        UNKNOWN
    }
    
    String mManifestUrl;

    File mTargetDir;
    
    byte[] mOriginManifestBytes;
    Period mCurrentPeriod;

    private long mItemDurationMS;
    private long mEstimatedDownloadSize;


    Map<DownloadItem.TrackType, List<DashTrack>> mSelectedTracks;
    Map<DownloadItem.TrackType, List<DashTrack>> mAvailableTracks;

    void parseOriginManifest() throws IOException {
        MediaPresentationDescriptionParser mpdParser = new MediaPresentationDescriptionParser();
        MediaPresentationDescription parsedMpd = mpdParser.parse(mManifestUrl, new ByteArrayInputStream(mOriginManifestBytes));

        if (parsedMpd.getPeriodCount() < 1) {
            throw new IOException("At least one period is required");
        }

        int periodIndex = 0;
        mCurrentPeriod = parsedMpd.getPeriod(periodIndex);
        setItemDurationMS(parsedMpd.getPeriodDuration(periodIndex));
        
    }

    void createDownloadTasks() throws MalformedURLException {
        
        mDownloadTasks = new LinkedHashSet<>();
        
        List<DashTrack> trackList = getSelectedTracks();
        for (DashTrack track : trackList) {
            AdaptationSet adaptationSet = mCurrentPeriod.adaptationSets.get(track.getAdaptationIndex());
            Representation representation = adaptationSet.representations.get(track.getRepresentationIndex());
            
            createDownloadTasks(representation, track);
        }

        if (AppBuildConfig.DEBUG) {
            Log.d(TAG, "download tasks: " + mDownloadTasks);
        }
    }

    private void createDownloadTasks(Representation representation, @NonNull DashTrack dashTrack) throws MalformedURLException {
        if (representation == null){
            return;
        }
        String reprId = representation.format.id;

        RangedUri initializationUri = representation.getInitializationUri();
        
        if (initializationUri != null) {
            addTask(initializationUri, "init-" + reprId + ".mp4", dashTrack.getRelativeId());
        }

        if (representation instanceof Representation.MultiSegmentRepresentation) {
            Representation.MultiSegmentRepresentation rep = (Representation.MultiSegmentRepresentation) representation;

            long periodDurationUs = getItemDurationMS() * 1000;
            int lastSegmentNum = rep.getLastSegmentNum(periodDurationUs);
            for (int segmentNum = rep.getFirstSegmentNum(); segmentNum <= lastSegmentNum; segmentNum++) {
                RangedUri url = rep.getSegmentUrl(segmentNum);
                addTask(url, "seg-" + reprId + "-" + segmentNum + ".m4s", dashTrack.getRelativeId());
            }
        
        } else if (representation instanceof Representation.SingleSegmentRepresentation) {
            Representation.SingleSegmentRepresentation rep = (Representation.SingleSegmentRepresentation) representation;
            if (rep.format.mimeType.equalsIgnoreCase("text/vtt")) {
                RangedUri url = rep.getIndex().getSegmentUrl(0);
                addTask(url, reprId + ".vtt", dashTrack.getRelativeId());
            }
        }
        
        setEstimatedDownloadSize(getEstimatedDownloadSize() + getItemDurationMS() *representation.format.bitrate / 8 / 1000);
    }

    long getItemDurationMS() {
        return mItemDurationMS;
    }

    void setItemDurationMS(long itemDurationMS) {
        mItemDurationMS = itemDurationMS;
    }

    void setEstimatedDownloadSize(long estimatedDownloadSize) {
        mEstimatedDownloadSize = estimatedDownloadSize;
    }


//    abstract void parse() throws IOException;
    
    long getEstimatedDownloadSize() {
        return mEstimatedDownloadSize;
    }

    String getPlaybackPath() {
        return LOCAL_MANIFEST_MPD;
    }

    LinkedHashSet<DownloadTask> getDownloadTasks() {
        return mDownloadTasks;
    }

    LinkedHashSet<DownloadTask> mDownloadTasks;

    DashDownloader(String manifestUrl, File targetDir) {
        mManifestUrl = manifestUrl;
        mTargetDir = targetDir;
        mDownloadTasks = new LinkedHashSet<>();
    }

    void createLocalManifest() throws IOException {

        // The localizer needs a raw list of tracks.
        List<DashTrack> tracks = getSelectedTracks();
        
        createLocalManifest(tracks, mOriginManifestBytes, mTargetDir);
    }

    static void createLocalManifest(List<DashTrack> tracks, byte[] originManifestBytes, File targetDir) throws IOException {
        DashManifestLocalizer localizer = new DashManifestLocalizer(originManifestBytes, tracks);
        localizer.localize();

        FileOutputStream outputStream = new FileOutputStream(new File(targetDir, LOCAL_MANIFEST_MPD));
        byte[] localManifestBytes = localizer.getLocalManifestBytes();
        outputStream.write(localManifestBytes);
        outputStream.close();

        if (AppBuildConfig.DEBUG) {
            Log.d(TAG, "local manifest: " + Base64.encodeToString(localManifestBytes, Base64.NO_WRAP));
        }
    }

    @NonNull
    List<DashTrack> getSelectedTracks() {
        return flattenTrackList(mSelectedTracks);
    }

    @NonNull
    static List<DashTrack> flattenTrackList(Map<DownloadItem.TrackType, List<DashTrack>> tracksMap) {
        List<DashTrack> tracks = new ArrayList<>();
        for (Map.Entry<DownloadItem.TrackType, List<DashTrack>> entry : tracksMap.entrySet()) {
            tracks.addAll(entry.getValue());
        }
        return tracks;
    }


    void addTask(RangedUri url, String file, String trackId) throws MalformedURLException {
        File targetFile = new File(mTargetDir, file);
        DownloadTask task = new DownloadTask(new URL(url.getUriString()), targetFile);
        task.trackRelativeId = trackId;
        mDownloadTasks.add(task);
    }

    
    void setSelectedTracks(@NonNull DownloadItem.TrackType type, @NonNull List<DashTrack> tracks) {
        // FIXME: 07/09/2016 Verify type, null
        mSelectedTracks.put(type, new ArrayList<>(tracks));
    }

    List<DashTrack> getAvailableTracks(DownloadItem.TrackType type) {
        // FIXME: 07/09/2016 Verify type
        return Collections.unmodifiableList(mAvailableTracks.get(type));
    }
    
    List<DashTrack> getAvailableTracks() {
        return flattenTrackList(mAvailableTracks);
    }


    abstract List<DashTrack> getDownloadedTracks(DownloadItem.TrackType type);

    abstract void apply() throws IOException;

    DownloadItem.TrackSelector getTrackSelector() {
        // TODO: 14/09/2016 Try to unify with DashDownloadCreator's selector 
        return new DownloadItem.TrackSelector() {
            private DashDownloader downloader = DashDownloader.this;

            @Override
            public List<DownloadItem.Track> getAvailableTracks(@NonNull final DownloadItem.TrackType type) {
                List<DashTrack> tracks = downloader.getAvailableTracks(type);
                return new ArrayList<DownloadItem.Track>(tracks);
            }

            @Override
            public void setSelectedTracks(@NonNull DownloadItem.TrackType type, @NonNull List<DownloadItem.Track> tracks) {
                
                List<DashTrack> dashTracks = new ArrayList<>(tracks.size());
                for (DownloadItem.Track track : tracks) {
                    // We don't have any other track class; leaving the potential ClassCastException on purpose. 
                    dashTracks.add((DashTrack) track);
                }
        
                downloader.setSelectedTracks(type, dashTracks);
            }

            @Override
            public List<DownloadItem.Track> getDownloadedTracks(@NonNull DownloadItem.TrackType type) {
                List<DashTrack> tracks = downloader.getDownloadedTracks(type);
                return new ArrayList<DownloadItem.Track>(tracks);
            }

            @Override
            public void apply() throws IOException {
                downloader.apply();
            }
        };
    }

}


