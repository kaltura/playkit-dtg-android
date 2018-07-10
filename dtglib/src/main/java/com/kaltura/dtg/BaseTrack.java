package com.kaltura.dtg;

import android.content.ContentValues;
import android.database.Cursor;
import android.support.annotation.NonNull;

import com.kaltura.android.exoplayer.chunk.Format;
import com.kaltura.dtg.dash.DashTrack;
import com.kaltura.dtg.hls.HlsAsset;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseTrack implements DownloadItem.Track {
    static final String[] REQUIRED_DB_FIELDS =
            {Database.COL_TRACK_ID, Database.COL_TRACK_TYPE, Database.COL_TRACK_LANGUAGE, Database.COL_TRACK_BITRATE, Database.COL_TRACK_EXTRA};

    private static final String EXTRA_WIDTH = "width";
    private static final String EXTRA_HEIGHT = "height";

    protected DownloadItem.TrackType type;
    protected String language;
    protected long bitrate;
    protected int width;
    protected int height;
    protected String codecs;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseTrack)) return false;

        BaseTrack baseTrack = (BaseTrack) o;

        if (bitrate != baseTrack.bitrate) return false;
        if (width != baseTrack.width) return false;
        if (height != baseTrack.height) return false;
        if (type != baseTrack.type) return false;
        if (language != null ? !language.equals(baseTrack.language) : baseTrack.language != null)
            return false;
        return codecs != null ? codecs.equals(baseTrack.codecs) : baseTrack.codecs == null;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (language != null ? language.hashCode() : 0);
        result = 31 * result + (int) (bitrate ^ (bitrate >>> 32));
        result = 31 * result + width;
        result = 31 * result + height;
        result = 31 * result + (codecs != null ? codecs.hashCode() : 0);
        return result;
    }

    public static BaseTrack create(Cursor cursor, AssetFormat assetFormat) {

        switch (assetFormat) {
            case hls:
                return new HlsAsset.Track(cursor);
            case dash:
                return new DashTrack(cursor);
            default:
                throw new IllegalArgumentException("Invalid AssetFormat " + assetFormat);
        }
    }

    public static List<BaseTrack> filterByLanguage(@NonNull String language, List<BaseTrack> list) {
        List<BaseTrack> filtered = new ArrayList<>();
        for (BaseTrack track : list) {
            if (language.equals(track.getLanguage())) {
                filtered.add(track);
            }
        }
        return filtered;
    }

    protected BaseTrack(DownloadItem.TrackType type, Format format) {
        this.type = type;
        this.bitrate = format.bitrate;
        this.codecs = format.codecs;
        this.height = format.height;
        this.width = format.width;
        this.language = format.language;
    }

    protected BaseTrack(DownloadItem.TrackType type, String language, long bitrate) {
        this.type = type;
        this.language = language;
        this.bitrate = bitrate;
    }

    protected BaseTrack(Cursor cursor) {
        String[] columns = cursor.getColumnNames();
        for (int i = 0; i < columns.length; i++) {
            switch (columns[i]) {
                case Database.COL_TRACK_TYPE:
                    type = DownloadItem.TrackType.valueOf(cursor.getString(i));
                    break;
                case Database.COL_TRACK_LANGUAGE:
                    language = cursor.getString(i);
                    break;
                case Database.COL_TRACK_BITRATE:
                    bitrate = cursor.getLong(i);
                    break;
                case Database.COL_TRACK_EXTRA:
                    parseExtra(cursor.getString(i));
                    break;
            }
        }
    }

    protected ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(Database.COL_TRACK_LANGUAGE, getLanguage());
        values.put(Database.COL_TRACK_BITRATE, getBitrate());
        values.put(Database.COL_TRACK_TYPE, getType().name());
        values.put(Database.COL_TRACK_REL_ID, getRelativeId());
        String extra = dumpExtra();
        if (extra != null) {
            values.put(Database.COL_TRACK_EXTRA, extra);
        }

        return values;
    }

    private String dumpExtra() {
        JSONObject jsonExtra = new JSONObject();
        try {
            jsonExtra
                    .put(EXTRA_HEIGHT, height)
                    .put(EXTRA_WIDTH, width);
            dumpExtra(jsonExtra);  // Subclasses

            return jsonExtra.toString();

        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void parseExtra(String extra) {
        JSONObject jsonExtra;
        try {
            jsonExtra = new JSONObject(extra);
            this.height = jsonExtra.optInt(EXTRA_HEIGHT, 0);
            this.width = jsonExtra.optInt(EXTRA_WIDTH, 0);
            parseExtra(jsonExtra);  // Subclasses

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    protected abstract void parseExtra(JSONObject jsonExtra);
    protected abstract void dumpExtra(JSONObject jsonExtra) throws JSONException;
    protected abstract String getRelativeId();

    @Override
    public DownloadItem.TrackType getType() {
        return type;
    }

    @Override
    public String getLanguage() {
        return language;
    }

    @Override
    public long getBitrate() {
        return bitrate;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getWidth() {
        return width;
    }

    public enum TrackState {
        NOT_SELECTED, SELECTED, DOWNLOADED,
        UNKNOWN
    }
}
