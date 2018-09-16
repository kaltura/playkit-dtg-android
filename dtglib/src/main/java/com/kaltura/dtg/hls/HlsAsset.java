package com.kaltura.dtg.hls;

import android.database.Cursor;
import android.net.Uri;

import com.kaltura.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.kaltura.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist.HlsUrl;
import com.kaltura.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.kaltura.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.kaltura.android.exoplayer2.source.hls.playlist.HlsPlaylistParser;
import com.kaltura.dtg.BaseTrack;
import com.kaltura.dtg.CodecSupport;
import com.kaltura.dtg.DownloadItem.TrackType;
import com.kaltura.dtg.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HlsAsset {

    private static final String TAG = "HlsAsset";
    static final HlsPlaylistParser parser = new HlsPlaylistParser();

    private String masterUrl;
    long durationMs;
    final List<Track> videoTracks = new ArrayList<>();
    final List<Track> audioTracks = new ArrayList<>();
    final List<Track> textTracks = new ArrayList<>();
    final Set<Integer> unsupportedTrackLines = new HashSet<>();

    HlsAsset() {
    }

    private static HlsPlaylist exoParse(final String url, final byte[] bytes) throws IOException {
        return parser.parse(Uri.parse(url), new ByteArrayInputStream(bytes));
    }

    public HlsAsset parse(final String masterUrl, final byte[] masterBytes) throws IOException {
        this.masterUrl = masterUrl;

        final HlsMasterPlaylist masterPlaylist = (HlsMasterPlaylist) exoParse(this.masterUrl, masterBytes);

        parseVariants(masterPlaylist.variants, videoTracks, TrackType.VIDEO);
        parseVariants(masterPlaylist.audios, audioTracks, TrackType.AUDIO);
        parseVariants(masterPlaylist.subtitles, textTracks, TrackType.TEXT);

        return this;
    }

    private void parseVariants(List<HlsUrl> variants, List<Track> trackList, TrackType trackType) {
        for (HlsUrl variant : variants) {
            // TODO: is this assumption (that VIDEO means the main content) correct?
            if (CodecSupport.isFormatSupported(variant.format, trackType == TrackType.VIDEO ? null : trackType)) {
                final Track track = new Track(variant, trackType, masterUrl);
                trackList.add(track);
            } else {
                unsupportedTrackLines.addAll(Utils.makeRange(variant.firstLineNum, variant.lastLineNum));
            }
        }
    }

    public static class Track extends BaseTrack {
        private static final String EXTRA_MASTER_FIRST_LINE = "masterFirstLine";
        private static final String EXTRA_MASTER_LAST_LINE = "masterLastLine";
        private static final String EXTRA_TRACK_URL = "url";

        long durationMs;
        String url;
        List<Chunk> chunks;
        private byte[] bytes;
        int firstMasterLine;
        int lastMasterLine;

        private Track(HlsUrl variant, TrackType trackType, String masterUrl) {
            super(trackType, variant.format);
            this.url = Utils.resolveUrl(masterUrl, variant.url);
            this.firstMasterLine = variant.firstLineNum;
            this.lastMasterLine = variant.lastLineNum;
        }

        public Track(Cursor cursor) {
            super(cursor);
        }

        public void parse(byte[] bytes) throws IOException {
            this.bytes = bytes;
            parse();
        }

        private void parse() throws IOException {
            final HlsMediaPlaylist mediaPlaylist = (HlsMediaPlaylist) exoParse(url, bytes);

            this.durationMs = mediaPlaylist.durationUs / 1000;

            this.chunks = new ArrayList<>(mediaPlaylist.segments.size());
            for (HlsMediaPlaylist.Segment segment : mediaPlaylist.segments) {
                this.chunks.add(new Chunk(segment, this.url));
            }
        }

        @Override
        protected void parseExtra(JSONObject jsonExtra) {
            this.firstMasterLine = jsonExtra.optInt(EXTRA_MASTER_FIRST_LINE, 0);
            this.lastMasterLine = jsonExtra.optInt(EXTRA_MASTER_LAST_LINE, 0);
            this.url = jsonExtra.optString(EXTRA_TRACK_URL);
        }

        @Override
        protected void dumpExtra(JSONObject jsonExtra) throws JSONException {
            jsonExtra.put(EXTRA_MASTER_FIRST_LINE, firstMasterLine)
                    .put(EXTRA_MASTER_LAST_LINE, lastMasterLine)
                    .put(EXTRA_TRACK_URL, url);
        }

        @Override
        protected String getRelativeId() {
            return "" + firstMasterLine;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Track)) return false;
            if (!super.equals(o)) return false;
            Track track = (Track) o;
            return durationMs == track.durationMs &&
                    firstMasterLine == track.firstMasterLine &&
                    Utils.equals(url, track.url);
        }

        @Override
        public int hashCode() {
            return Utils.hash(super.hashCode(), durationMs, url, firstMasterLine);
        }
    }

    static class Chunk {
        final int lineNum;
        final int encryptionKeyLineNum;
        final String url;
        final String encryptionKeyUri;
        final String ext;
        final String keyExt;

        Chunk(HlsMediaPlaylist.Segment segment, String trackUrl) {
            this.lineNum = segment.lineNum;
            this.url = Utils.resolveUrl(trackUrl, segment.url);
            this.encryptionKeyUri = Utils.resolveUrl(trackUrl, segment.fullSegmentEncryptionKeyUri);
            this.encryptionKeyLineNum = segment.encryptionKeyLineNum;
            ext = Utils.getExtension(segment.url);
            keyExt = Utils.getExtension(segment.fullSegmentEncryptionKeyUri);
        }
    }
}
