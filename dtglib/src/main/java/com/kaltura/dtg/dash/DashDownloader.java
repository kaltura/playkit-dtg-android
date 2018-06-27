package com.kaltura.dtg.dash;

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
import com.kaltura.dtg.BaseAbrDownloader;
import com.kaltura.dtg.BaseTrack;
import com.kaltura.dtg.DownloadItemImp;
import com.kaltura.dtg.DownloadService;
import com.kaltura.dtg.DownloadItem;
import com.kaltura.dtg.DownloadStateListener;
import com.kaltura.dtg.DownloadTask;
import com.kaltura.dtg.Utils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by noamt on 19/06/2016.
 */
public abstract class DashDownloader extends BaseAbrDownloader {

    static final String ORIGIN_MANIFEST_MPD = "origin.mpd";
    static final String LOCAL_MANIFEST_MPD = "local.mpd";
    static final int MAX_DASH_MANIFEST_SIZE = 10 * 1024 * 1024;
    private static final String TAG = "DashDownloader";
    String manifestUrl;

    byte[] originManifestBytes;
    Period currentPeriod;

    DashDownloader(String manifestUrl, File targetDir) {
        super(targetDir);
        this.manifestUrl = manifestUrl;
    }

    public static void start(DownloadService downloadService, DownloadItemImp item, File itemDataDir, DownloadStateListener downloadStateListener) throws IOException {
        final DashDownloader downloader = new DashDownloadCreator(item.getContentURL(), itemDataDir);

        DownloadItem.TrackSelector trackSelector = downloader.getTrackSelector();

        item.setTrackSelector(trackSelector);

        downloadStateListener.onTracksAvailable(item, trackSelector);

        downloader.apply();

        item.setTrackSelector(null);


        List<BaseTrack> availableTracks = Utils.flattenTrackList(downloader.availableTracks);
        List<BaseTrack> selectedTracks = downloader.getSelectedTracks();

        downloadService.addTracksToDB(item, availableTracks, selectedTracks);

        long estimatedDownloadSize = downloader.estimatedDownloadSize;
        item.setEstimatedSizeBytes(estimatedDownloadSize);

        LinkedHashSet<DownloadTask> downloadTasks = downloader.downloadTasks;
        //Log.d(TAG, "tasks:" + downloadTasks);

        item.setPlaybackPath(LOCAL_MANIFEST_MPD);

        downloadService.addDownloadTasksToDB(item, new ArrayList<>(downloadTasks));
    }

    private static void createLocalManifest(List<BaseTrack> tracks, byte[] originManifestBytes, File targetDir) throws IOException {
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

    @Override
    protected void parseOriginManifest() throws IOException {
        MediaPresentationDescriptionParser mpdParser = new MediaPresentationDescriptionParser();
        MediaPresentationDescription parsedMpd = mpdParser.parse(manifestUrl, new ByteArrayInputStream(originManifestBytes));

        if (parsedMpd.getPeriodCount() < 1) {
            throw new IOException("At least one period is required");
        }

        int periodIndex = 0;
        currentPeriod = parsedMpd.getPeriod(periodIndex);
        itemDurationMS = parsedMpd.getPeriodDuration(periodIndex);

    }

    @Override
    protected void createDownloadTasks() throws MalformedURLException {

        downloadTasks = new LinkedHashSet<>();

        List<BaseTrack> trackList = getSelectedTracks();
        for (BaseTrack bt : trackList) {
            DashTrack track = (DashTrack)bt;
            AdaptationSet adaptationSet = currentPeriod.adaptationSets.get(track.getAdaptationIndex());
            Representation representation = adaptationSet.representations.get(track.getRepresentationIndex());

            createDownloadTasks(representation, track);
        }

        //if (AppBuildConfig.DEBUG) {
        //    Log.d(TAG, "download tasks: " + downloadTasks);
        //}
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

            long periodDurationUs = itemDurationMS * 1000;
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

        estimatedDownloadSize += (itemDurationMS * representation.format.bitrate / 8 / 1000);
    }

    @Override
    protected void createLocalManifest() throws IOException {

        // The localizer needs a raw list of tracks.
        List<BaseTrack> tracks = getSelectedTracks();

        createLocalManifest(tracks, originManifestBytes, targetDir);
    }


    @Override
    public DownloadItem.TrackSelector getTrackSelector() {
        return new DownloadItem.TrackSelector() {
            private DashDownloader downloader = DashDownloader.this;

            @Override
            public List<DownloadItem.Track> getAvailableTracks(@NonNull final DownloadItem.TrackType type) {
                List<BaseTrack> tracks = downloader.getAvailableTracks(type);
                return new ArrayList<DownloadItem.Track>(tracks);
            }

            @Override
            public void setSelectedTracks(@NonNull DownloadItem.TrackType type, @NonNull List<DownloadItem.Track> tracks) {
                
                List<BaseTrack> dashTracks = new ArrayList<>(tracks.size());
                for (DownloadItem.Track track : tracks) {
                    // We don't have any other track class; leaving the potential ClassCastException on purpose.
                    // FIXME: 27/06/2018 Now we have HLS
                    dashTracks.add((DashTrack) track);
                }
        
                downloader.setSelectedTracks(type, dashTracks);
            }

            @Override
            public List<DownloadItem.Track> getDownloadedTracks(@NonNull DownloadItem.TrackType type) {
                List<BaseTrack> tracks = downloader.getDownloadedTracks(type);
                return new ArrayList<DownloadItem.Track>(tracks);
            }

            @Override
            public void apply() throws IOException {
                downloader.apply();
            }
        };
    }
}


