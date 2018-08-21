package com.kaltura.dtg.dash;

import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.kaltura.android.exoplayer.chunk.Format;
import com.kaltura.android.exoplayer.dash.mpd.AdaptationSet;
import com.kaltura.android.exoplayer.dash.mpd.MediaPresentationDescription;
import com.kaltura.android.exoplayer.dash.mpd.MediaPresentationDescriptionParser;
import com.kaltura.android.exoplayer.dash.mpd.Period;
import com.kaltura.android.exoplayer.dash.mpd.RangedUri;
import com.kaltura.android.exoplayer.dash.mpd.Representation;
import com.kaltura.dtg.AbrDownloader;
import com.kaltura.dtg.AssetFormat;
import com.kaltura.dtg.BaseTrack;
import com.kaltura.dtg.BuildConfig;
import com.kaltura.dtg.CodecSupport;
import com.kaltura.dtg.DownloadItem;
import com.kaltura.dtg.DownloadItemImp;
import com.kaltura.dtg.DownloadTask;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;

public class DashDownloader extends AbrDownloader {

    private static final String ORIGIN_MANIFEST_MPD = "origin.mpd";
    private static final String LOCAL_MANIFEST_MPD = "local.mpd";
    private static final String TAG = "DashDownloader";

    private Period currentPeriod;

    public DashDownloader(DownloadItemImp item) {
        super(item);
    }

    private static void createLocalManifest(List<BaseTrack> tracks, byte[] originManifestBytes, File targetDir) throws IOException {
        DashManifestLocalizer localizer = new DashManifestLocalizer(originManifestBytes, tracks);
        localizer.localize();

        FileOutputStream outputStream = new FileOutputStream(new File(targetDir, LOCAL_MANIFEST_MPD));
        byte[] localManifestBytes = localizer.getLocalManifestBytes();
        outputStream.write(localManifestBytes);
        outputStream.close();

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "local manifest: " + Base64.encodeToString(localManifestBytes, Base64.NO_WRAP));
        }
    }

    @Override
    protected AssetFormat getAssetFormat() {
        return AssetFormat.dash;
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
        setItemDurationMS(parsedMpd.getPeriodDuration(periodIndex));

    }

    @Override
    protected void createDownloadTasks() {

        setDownloadTasks(new LinkedHashSet<DownloadTask>());

        List<BaseTrack> trackList = getSelectedTracksFlat();
        for (BaseTrack bt : trackList) {
            DashTrack track = (DashTrack) bt;
            AdaptationSet adaptationSet = currentPeriod.adaptationSets.get(track.adaptationIndex);
            Representation representation = adaptationSet.representations.get(track.representationIndex);

            createDownloadTasks(representation, track);
        }

        //if (AppBuildConfig.DEBUG) {
        //    Log.d(TAG, "download tasks: " + downloadTasks);
        //}
    }

    private void createDownloadTasks(Representation representation, @NonNull DashTrack dashTrack) {
        if (representation == null) {
            return;
        }
        String reprId = representation.format.id;

        RangedUri initializationUri = representation.getInitializationUri();

        if (initializationUri != null) {
            addTask(initializationUri, "init-" + reprId + ".mp4", dashTrack.getRelativeId(), 0);
        }

        String ext = TextUtils.equals(representation.format.mimeType, "text/vtt") ? ".vtt" : ".m4s";

        if (representation instanceof Representation.MultiSegmentRepresentation) {
            Representation.MultiSegmentRepresentation rep = (Representation.MultiSegmentRepresentation) representation;

            long periodDurationUs = itemDurationMS * 1000;
            int lastSegmentNum = rep.getLastSegmentNum(periodDurationUs);
            for (int segmentNum = rep.getFirstSegmentNum(); segmentNum <= lastSegmentNum; segmentNum++) {
                RangedUri url = rep.getSegmentUrl(segmentNum);
                addTask(url, "seg-" + reprId + "-" + segmentNum + ext, dashTrack.getRelativeId(), segmentNum);
            }

        } else if (representation instanceof Representation.SingleSegmentRepresentation) {
            Representation.SingleSegmentRepresentation rep = (Representation.SingleSegmentRepresentation) representation;
            RangedUri url = rep.getIndex().getSegmentUrl(0);
            addTask(url, reprId + ext, dashTrack.getRelativeId(), 1);
        }

        setEstimatedDownloadSize(estimatedDownloadSize + (itemDurationMS * representation.format.bitrate / 8 / 1000));
    }

    @Override
    protected void createLocalManifest() throws IOException {

        // The localizer needs a raw list of tracks.
        List<BaseTrack> tracks = getSelectedTracksFlat();

        createLocalManifest(tracks, originManifestBytes, getTargetDir());
    }

    @Override
    public String storedOriginManifestName() {
        return ORIGIN_MANIFEST_MPD;
    }

    @Override
    public String storedLocalManifestName() {
        return LOCAL_MANIFEST_MPD;
    }

    private void addTask(RangedUri url, String file, String trackId, int order) {
        File targetFile = new File(getTargetDir(), file);
        DownloadTask task = new DownloadTask(url.getUri(), targetFile, order);
        task.setTrackRelativeId(trackId);
        task.setOrder(order);
        downloadTasks.add(task);
    }

    @Override
    protected void createTracks() {

        List<AdaptationSet> adaptationSets = currentPeriod.adaptationSets;

        setAvailableTracksMap(new HashMap<DownloadItem.TrackType, List<BaseTrack>>());
        for (DownloadItem.TrackType type : DownloadItem.TrackType.values()) {
            setAvailableTracksByType(type, new ArrayList<BaseTrack>(1));
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
                        Log.w(TAG, "createTracks: Unknown AdaptationSet type: " + adaptationSet.type);
                        continue;
                }

                Format format = representation.format;

                if (!CodecSupport.isFormatSupported(format, type)) {
                    continue;
                }

                DashTrack track = new DashTrack(type, format, adaptationIndex, representationIndex);
                track.setHeight(format.height);
                track.setWidth(format.width);
                availableTracks.get(type).add(track);
            }
        }
    }

}


