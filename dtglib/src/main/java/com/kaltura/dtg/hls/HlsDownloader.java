package com.kaltura.dtg.hls;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.kaltura.android.exoplayer2.ParserException;
import com.kaltura.dtg.AbrDownloader;
import com.kaltura.dtg.AssetFormat;
import com.kaltura.dtg.BaseTrack;
import com.kaltura.dtg.DownloadItem;
import com.kaltura.dtg.DownloadItemImp;
import com.kaltura.dtg.DownloadTask;
import com.kaltura.dtg.Utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HlsDownloader extends AbrDownloader {
    private static final String TAG = "HlsDownloader";
    private static final String ORIGIN_M3U8 = "origin.m3u8";
    private static final String LOCAL_MASTER = "master.m3u8";
    private static final String LOCAL_MEDIA = "media.m3u8";
    private final int defaultHlsAudioBitrateEstimation;

    private HlsAsset hlsAsset;

    public HlsDownloader(DownloadItemImp item, int defaultHlsAudioBitrateEstimation) {
        super(item);
        this.defaultHlsAudioBitrateEstimation = defaultHlsAudioBitrateEstimation;
    }

    private static void maybeAddTask(LinkedHashSet<DownloadTask> tasks, String relativeId, File trackTargetDir, int lineNum, String type, String url, int order) {
        if (url == null) {
            return;
        }
        final File file = new File(trackTargetDir, getLocalMediaFilename(lineNum, type));
        final DownloadTask task = new DownloadTask(Uri.parse(url), file, order);
        task.setTrackRelativeId(relativeId);
        tasks.add(task);
    }

    private static String getLocalMediaFilename(int lineNum, String type) {
        return String.format(Locale.US, "%06d.%s", lineNum, type);
    }

    @Override
    public void parseOriginManifest() throws IOException {
        this.hlsAsset = new HlsAsset().parse(manifestUrl, originManifestBytes);
    }

    @Override
    public void createDownloadTasks() throws IOException {

        final LinkedHashSet<DownloadTask> tasks = new LinkedHashSet<>();
        List<BaseTrack> trackList = getSelectedTracksFlat();

        long totalEstimatedSize = 0;
        long maxDuration = 0;

        for (BaseTrack baseTrack : trackList) {
            final HlsAsset.Track track = (HlsAsset.Track) baseTrack;
            final File trackTargetDir = getTrackTargetDir(track);

            if (track.chunks == null) {
                readOrDownloadTrackPlaylist(track);
            }

            int order = 0;
            for (HlsAsset.Chunk chunk : track.chunks) {
                order++;
                maybeAddTask(tasks, track.getRelativeId(), trackTargetDir, chunk.lineNum, chunk.ext, chunk.url, order);
                maybeAddTask(tasks, track.getRelativeId(), trackTargetDir, chunk.encryptionKeyLineNum, chunk.keyExt, chunk.encryptionKeyUri, order);
            }

            maxDuration = Math.max(maxDuration, track.durationMs);

            // Update size
            long bitrate = track.getBitrate() > 0 ? track.getBitrate() :
                    track.getType() == DownloadItem.TrackType.AUDIO ? defaultHlsAudioBitrateEstimation : 0;

            if (bitrate > 0 && track.durationMs > 0) {
                totalEstimatedSize += bitrate * track.durationMs / 1000 / 8;
            }
        }

        setDownloadTasks(tasks);
        setItemDurationMS(maxDuration);
        setEstimatedDownloadSize(totalEstimatedSize);
    }

    @NonNull
    private File getTrackTargetDir(HlsAsset.Track track) {
        return new File(getTargetDir(), "track-" + track.getRelativeId());
    }

    @Override
    public void createLocalManifest() throws IOException {

        createLocalMasterPlaylist();

        // Now localize the media playlists
        createLocalMediaPlaylist(hlsAsset.videoTracks);
        createLocalMediaPlaylist(hlsAsset.audioTracks);
        createLocalMediaPlaylist(hlsAsset.textTracks);
    }

    @Override
    public AssetFormat getAssetFormat() {
        return AssetFormat.hls;
    }

    @Override
    public String storedOriginManifestName() {
        return ORIGIN_M3U8;
    }

    @Override
    public String storedLocalManifestName() {
        return LOCAL_MASTER;
    }

    private void createLocalMediaPlaylist(List<HlsAsset.Track> tracks) throws IOException {
        for (HlsAsset.Track track : tracks) {
            final File trackTargetDir = getTrackTargetDir(track);
            final File originFile = new File(trackTargetDir, ORIGIN_M3U8);
            if (!originFile.canRead()) {
                continue;
            }

            LineNumberReader reader = null;
            Writer writer = null;

            try {
                reader = new LineNumberReader(new FileReader(originFile));
                writer = new BufferedWriter(new FileWriter(new File(trackTargetDir, LOCAL_MEDIA)));

                String line;
                while ((line = reader.readLine()) != null) {
                    final int lineNumber = reader.getLineNumber();
                    // Fix URI
                    line = maybeReplaceUri(line, lineNumber);
                    writer.write(line);
                    writer.write('\n');
                }
            } finally {
                Utils.safeClose(writer);
                Utils.safeClose(reader);
            }
        }
    }

    private void createLocalMasterPlaylist() throws IOException {
        Set<Integer> linesToDelete = new HashSet<>();

        for (BaseTrack baseTrack : getAvailableTracksFlat()) {
            final HlsAsset.Track track = (HlsAsset.Track) baseTrack;
            linesToDelete.addAll(Utils.makeRange(track.firstMasterLine, track.lastMasterLine));
        }

        for (BaseTrack baseTrack : getSelectedTracksFlat()) {
            final HlsAsset.Track track = (HlsAsset.Track) baseTrack;
            linesToDelete.removeAll(Utils.makeRange(track.firstMasterLine, track.lastMasterLine));
        }

        linesToDelete.addAll(hlsAsset.unsupportedTrackLines);

        // Now linesToDelete is a set of lines that should be DELETED from the master
        LineNumberReader reader = null;
        PrintWriter writer = null;
        int trackLineNumber = 0;
        try {
            reader = new LineNumberReader(new FileReader(getOriginalMasterFile()));
            writer = new PrintWriter(getLocalMasterFile());

            String line;
            while ((line = reader.readLine()) != null) {
                final int lineNumber = reader.getLineNumber();
                if (!linesToDelete.contains(lineNumber)) {
                    // Fix URI
                    if (TextUtils.isEmpty(line)) {
                        writer.println();
                    } else if (line.charAt(0) == '#') {
                        if (line.startsWith("#EXT-X-STREAM-INF:")) {
                            writer.println(line);
                            trackLineNumber = lineNumber;
                        } else if (line.startsWith("#EXT-X-I-FRAME-STREAM-INF:")) {
                            // skip
                            trackLineNumber = 0;
                        } else {
                            writer.println(maybeReplaceTrackUri(line, lineNumber));
                            trackLineNumber = 0;
                        }
                    } else {
                        writer.println(maybeReplaceTrackUri(line, trackLineNumber));
                    }
                }
            }
        } finally {
            Utils.safeClose(writer);
            Utils.safeClose(reader);
        }
    }

    @NonNull
    private File getLocalMasterFile() {
        return new File(getTargetDir(), LOCAL_MASTER);
    }

    private String maybeReplaceUri(@NonNull String line, int lineNum) {

        String uri = extractUri(line);
        if (uri != null) {
            final String type = Utils.getExtension(uri);
            return line.replace(uri, getLocalMediaFilename(lineNum, type));
        }
        return line;
    }

    private String maybeReplaceTrackUri(@NonNull String line, int lineNum) {
        String uri = extractUri(line);
        if (uri != null) {
            return line.replace(uri, "track-" + lineNum + "/media.m3u8");
        }
        return line;
    }

    private String extractUri(@NonNull String line) {
        if (line.startsWith("#")) {
            try {
                return HlsAsset.parser.extractUriAttribute(line);
            } catch (ParserException e) {
                // nothing, no URI in this line
                return null;
            }
        }
        return TextUtils.isEmpty(line) ? null : line;
    }

    @NonNull
    private File getOriginalMasterFile() {
        return new File(getTargetDir(), ORIGIN_M3U8);
    }

    @Override
    protected void createTracks() {

        Map<DownloadItem.TrackType, List<BaseTrack>> availableTracks = new HashMap<>();

        for (DownloadItem.TrackType trackType : DownloadItem.TrackType.values()) {
            availableTracks.put(trackType, new ArrayList<BaseTrack>());
        }

        addTracks(availableTracks, hlsAsset.videoTracks, DownloadItem.TrackType.VIDEO);
        addTracks(availableTracks, hlsAsset.audioTracks, DownloadItem.TrackType.AUDIO);
        addTracks(availableTracks, hlsAsset.textTracks, DownloadItem.TrackType.TEXT);

        setAvailableTracksMap(availableTracks);
    }

    private void addTracks(Map<DownloadItem.TrackType, List<BaseTrack>> trackMap, List<HlsAsset.Track> tracks, DownloadItem.TrackType trackType) {
        for (HlsAsset.Track track : tracks) {
            trackMap.get(trackType).add(track);
        }
    }

    @Override
    protected void applyInitialTrackSelection() throws IOException {
        if (trackSelectionApplied) {
            Log.w(TAG, "Ignoring unsupported extra call to apply()");
            return;
        }

        // Download
        for (BaseTrack bt : getSelectedTracksFlat()) {
            readOrDownloadTrackPlaylist((HlsAsset.Track) bt);
        }

        super.applyInitialTrackSelection();
    }

    private void readOrDownloadTrackPlaylist(HlsAsset.Track track) throws IOException {
        final File trackTargetDir = getTrackTargetDir(track);
        final File targetFile = new File(trackTargetDir, ORIGIN_M3U8);
        final byte[] bytes;
        if (trackTargetDir.isDirectory() && targetFile.canRead()) {
            // Read stored track playlist
            bytes = Utils.readFile(targetFile, MAX_MANIFEST_SIZE);
        } else {
            Utils.mkdirsOrThrow(trackTargetDir);
            bytes = Utils.downloadToFile(track.url, targetFile, MAX_MANIFEST_SIZE);
        }
        track.parse(bytes);
    }
}
