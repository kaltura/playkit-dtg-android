package com.kaltura.dtg;

import android.content.ContentValues;
import android.database.Cursor;
import android.support.annotation.NonNull;

import com.kaltura.dtg.dash.DashTrack;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseTrack implements DownloadItem.Track {
    static final String[] REQUIRED_DB_FIELDS =
            {Database.COL_TRACK_ID, Database.COL_TRACK_TYPE, Database.COL_TRACK_LANGUAGE, Database.COL_TRACK_BITRATE, Database.COL_TRACK_EXTRA};
    protected DownloadItem.TrackType type;
    protected String language;
    protected long bitrate;
    protected int width;
    protected int height;

    public static BaseTrack create(Cursor cursor) {
        // TODO: 26/06/2018 Detect DASH/HLS track
        return new DashTrack(cursor);
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

    protected BaseTrack(DownloadItem.TrackType type, String language, long bitrate) {
        this.type = type;
        this.language = language;
        this.bitrate = bitrate;
    }

    protected BaseTrack(ContentValues contentValues) {
        parseContentValues(contentValues);
    }

    protected BaseTrack(Cursor cursor) {
        parseCursor(cursor);
    }

    private void parseContentValues(ContentValues contentValues) {
        bitrate = contentValues.getAsLong(Database.COL_TRACK_BITRATE);
        language = contentValues.getAsString(Database.COL_TRACK_LANGUAGE);
        String typeName = contentValues.getAsString(Database.COL_TRACK_TYPE);
        type = DownloadItem.TrackType.valueOf(typeName);
        String extra = contentValues.getAsString(Database.COL_TRACK_EXTRA);
        parseExtra(extra);
    }

    private void parseCursor(Cursor cursor) {
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

    protected abstract void parseExtra(String extra);

    protected ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(Database.COL_TRACK_LANGUAGE, getLanguage());
        values.put(Database.COL_TRACK_BITRATE, getBitrate());
        values.put(Database.COL_TRACK_TYPE, getType().name());
        values.put(Database.COL_TRACK_REL_ID, getRelativeId());
        JSONObject extra = dumpExtra();
        if (extra != null) {
            values.put(Database.COL_TRACK_EXTRA, extra.toString());
        }

        return values;
    }

    protected abstract JSONObject dumpExtra();

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

    public abstract String getRelativeId();

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public enum TrackState {
        NOT_SELECTED, SELECTED, DOWNLOADED,
        UNKNOWN
    }
}
