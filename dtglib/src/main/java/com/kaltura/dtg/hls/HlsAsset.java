package com.kaltura.dtg.hls;

import android.database.Cursor;

import com.kaltura.android.exoplayer.hls.HlsMasterPlaylist;
import com.kaltura.android.exoplayer.hls.HlsMediaPlaylist;
import com.kaltura.android.exoplayer.hls.HlsPlaylist;
import com.kaltura.android.exoplayer.hls.HlsPlaylistParser;
import com.kaltura.android.exoplayer.hls.Variant;
import com.kaltura.dtg.BaseTrack;
import com.kaltura.dtg.DownloadItem.TrackType;
import com.kaltura.dtg.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HlsAsset {

    private static final String TAG = "HlsAsset";
    static final HlsPlaylistParser parser = new HlsPlaylistParser();

    private String masterUrl;
    long durationMs;
    final List<Track> videoTracks = new ArrayList<>();
    final List<Track> audioTracks = new ArrayList<>();
    final List<Track> textTracks = new ArrayList<>();

    HlsAsset() {
    }

    private static HlsPlaylist exoParse(final String url, final byte[] bytes) {
        try {
            return parser.parse(url, new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new IllegalStateException("Not possible");
        }
    }

    public HlsAsset parse(final String masterUrl, final byte[] masterBytes) {
        this.masterUrl = masterUrl;

        final HlsMasterPlaylist masterPlaylist = (HlsMasterPlaylist) exoParse(this.masterUrl, masterBytes);

        parseVariants(masterPlaylist.variants, videoTracks, TrackType.VIDEO);
        parseVariants(masterPlaylist.audios, audioTracks, TrackType.AUDIO);
        parseVariants(masterPlaylist.subtitles, textTracks, TrackType.TEXT);

        return this;
    }

    private void parseVariants(List<Variant> variants, List<Track> trackList, TrackType trackType) {
        for (Variant variant : variants) {

            final Track track = new Track(variant, trackType, masterUrl);
            trackList.add(track);
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

        private Track(Variant variant, TrackType trackType, String masterUrl) {
            super(trackType, variant.format);
            this.url = Utils.resolveUrl(masterUrl, variant.url);
            this.firstMasterLine = variant.firstLineNum;
            this.lastMasterLine = variant.lastLineNum;
        }

        public Track(Cursor cursor) {
            super(cursor);
        }

        public void parse(byte[] bytes) {
            this.bytes = bytes;
            parse();
        }

        private void parse() {
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

        Chunk(HlsMediaPlaylist.Segment segment, String trackUrl) {
            this.lineNum = segment.lineNum;
            this.url = Utils.resolveUrl(trackUrl, segment.url);
            this.encryptionKeyUri = Utils.resolveUrl(trackUrl, segment.encryptionKeyUri);
            this.encryptionKeyLineNum = segment.encryptionKeyLineNum;
        }
    }
}
