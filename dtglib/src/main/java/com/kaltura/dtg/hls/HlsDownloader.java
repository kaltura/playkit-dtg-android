package com.kaltura.dtg.hls;

import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.kaltura.android.exoplayer.ParserException;
import com.kaltura.dtg.BaseAbrDownloader;
import com.kaltura.dtg.BaseTrack;
import com.kaltura.dtg.DownloadItem;
import com.kaltura.dtg.DownloadItemImp;
import com.kaltura.dtg.DownloadService;
import com.kaltura.dtg.DownloadStateListener;
import com.kaltura.dtg.DownloadTask;
import com.kaltura.dtg.Utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public abstract class HlsDownloader extends BaseAbrDownloader {
    private static final String TAG = "HlsDownloader";
    protected static final int MAX_PLAYLIST_SIZE = 10 * 1024 * 1024;
    protected static final String ORIGIN_M3U8 = "origin.m3u8";
    protected static final String LOCAL_MASTER = "master.m3u8";
    protected static final String LOCAL_MEDIA = "media.m3u8";

    protected final String masterPlaylistUrl;

    protected byte[] originMasterPlaylistBytes;
    protected HlsAsset hlsAsset;

    HlsDownloader(String masterPlaylistUrl, File targetDir) {
        super(targetDir);
        this.masterPlaylistUrl = masterPlaylistUrl;
    }

    public static void start(DownloadService downloadService, DownloadItemImp item, File itemDataDir, DownloadStateListener downloadStateListener) throws IOException {
        final HlsDownloader downloader = new HlsDownloadCreator(item.getContentURL(), itemDataDir);

        DownloadItem.TrackSelector trackSelector = downloader.getTrackSelector();

        item.setTrackSelector(trackSelector);

        downloadStateListener.onTracksAvailable(item, trackSelector);

        downloader.apply();

        item.setTrackSelector(null);


        List<BaseTrack> availableTracks = Utils.flattenTrackList(downloader.getAvailableTracks());
        List<BaseTrack> selectedTracks = downloader.getSelectedTracksFlat();

        downloadService.addTracksToDB(item, availableTracks, selectedTracks);

        long estimatedDownloadSize = downloader.getEstimatedDownloadSize();
        item.setEstimatedSizeBytes(estimatedDownloadSize);

        LinkedHashSet<DownloadTask> downloadTasks = downloader.getDownloadTasks();
        //Log.d(TAG, "tasks:" + downloadTasks);

        item.setPlaybackPath(LOCAL_MASTER);

        downloadService.addDownloadTasksToDB(item, new ArrayList<>(downloadTasks));

    }

    @Override
    protected void parseOriginManifest() throws IOException {
        this.hlsAsset = new HlsAsset().parse(masterPlaylistUrl, originMasterPlaylistBytes);
    }

    @Override
    protected void createDownloadTasks() throws IOException {

        final LinkedHashSet<DownloadTask> tasks = new LinkedHashSet<>();
        List<BaseTrack> trackList = getSelectedTracksFlat();

        Log.d(TAG, "createDownloadTasks: " + trackList);

        for (BaseTrack baseTrack : trackList) {
            final HlsAsset.Track track = (HlsAsset.Track) baseTrack;
            final File trackTargetDir = getTrackTargetDir(track);
            for (HlsAsset.Chunk chunk : track.chunks) {
                maybeAddTask(tasks, track.getRelativeId(), trackTargetDir, chunk.lineNum, "media", chunk.url);
                maybeAddTask(tasks, track.getRelativeId(), trackTargetDir, chunk.encryptionKeyLineNum, "key", chunk.encryptionKeyUri);
            }
        }

        setDownloadTasks(tasks);
    }

    private static void maybeAddTask(LinkedHashSet<DownloadTask> tasks, String relativeId, File trackTargetDir, int lineNum, String type, String url) throws MalformedURLException {
        if (url == null) {
            return;
        }
        final File file = new File(trackTargetDir, getLocalMediaFilename(lineNum, type));
        final DownloadTask task = new DownloadTask(new URL(url), file);
        task.setTrackRelativeId(relativeId);
        tasks.add(task);
    }

    private static String getLocalMediaFilename(int lineNum, String type) {
        return String.format(Locale.US, "%06d.%s", lineNum, type);
    }

    @NonNull
    File getTrackTargetDir(HlsAsset.Track track) {
        return new File(getTargetDir(), "track-" + track.getRelativeId());
    }

    private static Set<Integer> makeRange(int first, int last) {
        if (last < first) {
            return Collections.singleton(first);
        }
        Set<Integer> range = new HashSet<>();
        for (int i = first; i <= last; i++) {
            range.add(i);
        }
        return range;
    }

    @Override
    protected void createLocalManifest() throws IOException {

        createLocalMasterPlaylist();

        // Now localize the media playlists
        createLocalMediaPlaylist(hlsAsset.videoTracks);
        createLocalMediaPlaylist(hlsAsset.audioTracks);
        createLocalMediaPlaylist(hlsAsset.textTracks);
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
                    final String type = HlsAsset.parser.containsEncryptionKey(line) ? "key" : "media";
                    line = maybeReplaceUri(line, lineNumber, type);
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
            linesToDelete.addAll(makeRange(track.firstMasterLine, track.lastMasterLine));
        }

        for (BaseTrack baseTrack : getSelectedTracksFlat()) {
            final HlsAsset.Track track = (HlsAsset.Track) baseTrack;
            linesToDelete.removeAll(makeRange(track.firstMasterLine, track.lastMasterLine));
        }

        // Now linesToDelete is a set of lines that should be DELETED from the master
        LineNumberReader reader = null;
        Writer writer = null;
        try {
            reader = new LineNumberReader(new FileReader(getOriginalMasterFile()));
            writer = new BufferedWriter(new FileWriter(getLocalMasterFile()));

            String line;
            while ((line = reader.readLine()) != null) {
                final int lineNumber = reader.getLineNumber();
                if (! linesToDelete.contains(lineNumber)) {
                    // Fix URI
                    line = maybeReplaceTrackUri(line, lineNumber);
                    writer.write(line);
                }
                writer.write('\n');
            }
        } finally {
            Utils.safeClose(writer);
            Utils.safeClose(reader);
        }
    }

    @NonNull
    File getLocalMasterFile() {
        return new File(getTargetDir(), LOCAL_MASTER);
    }

    private String maybeReplaceUri(@NonNull String line, int lineNum, Object type) {

        String uri = extractUri(line);
        if (uri != null) {
            return line.replace(uri, getLocalMediaFilename(lineNum, (String) type));
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

    protected void downloadMasterPlaylist() throws IOException {
        URL url = new URL(masterPlaylistUrl);
        File targetFile = getOriginalMasterFile();
        originMasterPlaylistBytes = Utils.downloadToFile(url, targetFile, MAX_PLAYLIST_SIZE);
    }

    @NonNull
    File getOriginalMasterFile() {
        return new File(getTargetDir(), ORIGIN_M3U8);
    }
}
