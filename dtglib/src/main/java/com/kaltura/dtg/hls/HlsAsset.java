package com.kaltura.dtg.hls;

import android.database.Cursor;
import android.util.Log;

import com.kaltura.android.exoplayer.hls.HlsMasterPlaylist;
import com.kaltura.android.exoplayer.hls.HlsMediaPlaylist;
import com.kaltura.android.exoplayer.hls.HlsPlaylist;
import com.kaltura.android.exoplayer.hls.HlsPlaylistParser;
import com.kaltura.android.exoplayer.hls.Variant;
import com.kaltura.dtg.BaseTrack;
import com.kaltura.dtg.DownloadItem;
import com.kaltura.dtg.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HlsAsset implements Serializable {

    private static final String TAG = "HlsAsset";
    private static final long serialVersionUID = 764126803479953467L;

    String masterUrl;
    long durationMs;
    List<Track> videoTracks = new ArrayList<>();
    List<Track> audioTracks = new ArrayList<>();
    List<Track> textTracks = new ArrayList<>();
    static transient HlsPlaylistParser parser = new HlsPlaylistParser();
    byte[] masterBytes;

    public HlsAsset() {
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
        this.masterBytes = masterBytes;
        parse();
        return this;
    }

    private void parse() {
        final HlsMasterPlaylist masterPlaylist = (HlsMasterPlaylist) exoParse(masterUrl, masterBytes);

        parseVariants(masterPlaylist.variants, videoTracks, DownloadItem.TrackType.VIDEO);
        parseVariants(masterPlaylist.audios, audioTracks, DownloadItem.TrackType.AUDIO);
        parseVariants(masterPlaylist.subtitles, textTracks, DownloadItem.TrackType.TEXT);
    }

    public String getMasterUrl() {
        return masterUrl;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public List<Track> getVideoTracks() {
        return Collections.unmodifiableList(videoTracks);
    }

    public List<Track> getAudioTracks() {
        return Collections.unmodifiableList(audioTracks);
    }

    public List<Track> getTextTracks() {
        return Collections.unmodifiableList(textTracks);
    }

    private void parseVariants(List<Variant> variants, List<Track> trackList, DownloadItem.TrackType trackType) {
        for (Variant variant : variants) {

            final Track track = new Track(variant, trackType);
            trackList.add(track);
        }
    }

    public static class Track extends BaseTrack implements Serializable {
        private static final long serialVersionUID = -1436376252417539032L;
        private static final String ORIGINAL_MASTER_FIRST_LINE = "ORIGINAL_MASTER_FIRST_LINE";
        private static final String ORIGINAL_MASTER_LAST_LINE = "ORIGINAL_MASTER_LAST_LINE";

        long durationMs;
        String url;
        List<Chunk> chunks;
        transient byte[] bytes;
        int firstMasterLine;
        int lastMasterLine;

        Track(Variant variant, DownloadItem.TrackType trackType) {
            super(trackType, variant.format);
            this.url = variant.url;
            this.firstMasterLine = variant.firstLineNum;
            this.lastMasterLine = variant.lastLineNum;
        }

        public Track(Cursor cursor) {
            super(cursor);
        }

        public Track parse(byte[] bytes) {
            this.bytes = bytes;
            parse();
            return this;
        }

        private void parse() {
            final HlsMediaPlaylist mediaPlaylist = (HlsMediaPlaylist) exoParse(url, bytes);

            this.durationMs = mediaPlaylist.durationUs / 1000;

            this.chunks = new ArrayList<>(mediaPlaylist.segments.size());
            for (HlsMediaPlaylist.Segment segment : mediaPlaylist.segments) {
                this.chunks.add(new Chunk(segment, this.url));
            }
        }

        public long getDurationMs() {
            return durationMs;
        }

        public String getUrl() {
            return url;
        }

        public List<Chunk> getChunks() {
            return Collections.unmodifiableList(chunks);
        }

        @Override
        protected void parseExtra(String extra) {
            JSONObject jsonExtra;
            try {
                jsonExtra = new JSONObject(extra);
                firstMasterLine = jsonExtra.optInt(ORIGINAL_MASTER_FIRST_LINE, 0);
                lastMasterLine = jsonExtra.optInt(ORIGINAL_MASTER_LAST_LINE, 0);
            } catch (JSONException e) {
                Log.e(TAG, "Can't parse track extra", e);
            }

        }

        @Override
        protected String dumpExtra() {
            try {
                return new JSONObject()
                        .put(ORIGINAL_MASTER_FIRST_LINE, firstMasterLine)
                        .put(ORIGINAL_MASTER_LAST_LINE, lastMasterLine)
                        .toString();
            } catch (JSONException e) {
                Log.e(TAG, "Can't dump track extra", e);
                return null;
            }
        }

        @Override
        protected String getRelativeId() {
            return String.valueOf(lastMasterLine > 0 ? lastMasterLine : firstMasterLine);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;

            Track track = (Track) o;

            if (durationMs != track.durationMs) return false;
            if (firstMasterLine != track.firstMasterLine) return false;
            if (lastMasterLine != track.lastMasterLine) return false;
            return url != null ? url.equals(track.url) : track.url == null;
        }

        @Override
        public int hashCode() {
            int result = (int) (durationMs ^ (durationMs >>> 32));
            result = 31 * result + (url != null ? url.hashCode() : 0);
            result = 31 * result + firstMasterLine;
            result = 31 * result + lastMasterLine;
            result = 31 * result + super.hashCode();
            return result;
        }
    }

    static class Chunk implements Serializable {

        private static final long serialVersionUID = -2469901304472643052L;
        final int lineNum;
        final int encryptionKeyLineNum;

        String url;
        String encryptionKeyUri;

        Chunk(HlsMediaPlaylist.Segment segment, String trackUrl) {
            this.lineNum = segment.lineNum;
            this.url = Utils.resolveUrl(trackUrl, segment.url);
            this.encryptionKeyUri = Utils.resolveUrl(trackUrl, segment.encryptionKeyUri);
            this.encryptionKeyLineNum = segment.encryptionKeyLineNum;
        }
    }
}
