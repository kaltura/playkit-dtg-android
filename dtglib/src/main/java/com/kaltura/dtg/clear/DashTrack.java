package com.kaltura.dtg.clear;

import android.content.ContentValues;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.util.Log;

import com.kaltura.dtg.DownloadItem;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by noamt on 13/09/2016.
 */
class DashTrack implements DownloadItem.Track {
    public static final String ORIGINAL_ADAPTATION_SET_INDEX = "originalAdaptationSetIndex";
    public static final String ORIGINAL_REPRESENTATION_INDEX = "originalRepresentationIndex";
    private static final String TAG = "DashTrack";
    private int adaptationIndex;
    private int representationIndex;
    private DownloadItem.TrackType type;
    private String language;
    private long bitrate;

    static final String[] REQUIRED_DB_FIELDS =
            {Database.COL_TRACK_ID, Database.COL_TRACK_TYPE, Database.COL_TRACK_LANGUAGE, Database.COL_TRACK_BITRATE, Database.COL_TRACK_EXTRA};
    

    DashTrack(DownloadItem.TrackType type, String language, long bitrate, int adaptationIndex, int representationIndex) {
        this.type = type;
        this.language = language;
        this.bitrate = bitrate;
        this.adaptationIndex = adaptationIndex;
        this.representationIndex = representationIndex;
    }

    DashTrack(ContentValues contentValues) {
        bitrate = contentValues.getAsLong(Database.COL_TRACK_BITRATE);
        language = contentValues.getAsString(Database.COL_TRACK_LANGUAGE);
        String typeName = contentValues.getAsString(Database.COL_TRACK_TYPE);
        type = DownloadItem.TrackType.valueOf(typeName);
        String extra = contentValues.getAsString(Database.COL_TRACK_EXTRA);
        JSONObject jsonExtra;
        try {
            jsonExtra = new JSONObject(extra);
            adaptationIndex = jsonExtra.optInt(ORIGINAL_ADAPTATION_SET_INDEX, 0);
            representationIndex = jsonExtra.optInt(ORIGINAL_REPRESENTATION_INDEX, 0);
        } catch (JSONException e) {
            Log.e(TAG, "Can't parse track extra", e);
        }
    }

    DashTrack(Cursor cursor) {
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

    private void parseExtra(String extra) {
        JSONObject jsonExtra;
        try {
            jsonExtra = new JSONObject(extra);
            adaptationIndex = jsonExtra.optInt(ORIGINAL_ADAPTATION_SET_INDEX, 0);
            representationIndex = jsonExtra.optInt(ORIGINAL_REPRESENTATION_INDEX, 0);
        } catch (JSONException e) {
            Log.e(TAG, "Can't parse track extra", e);
        }
    }

    ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(Database.COL_TRACK_LANGUAGE, getLanguage());
        values.put(Database.COL_TRACK_BITRATE, getBitrate());
        values.put(Database.COL_TRACK_TYPE, getType().name());
        values.put(Database.COL_TRACK_REL_ID, getRelativeId());
        JSONObject extra = null;
        try {
            extra = new JSONObject()
                    .put(ORIGINAL_ADAPTATION_SET_INDEX, getAdaptationIndex())
                    .put(ORIGINAL_REPRESENTATION_INDEX, getRepresentationIndex());
        } catch (JSONException e) {
            Log.e(TAG, "Failed converting to JSON");
        }
        if (extra != null) {
            values.put(Database.COL_TRACK_EXTRA, extra.toString());
        }

        return values;
    }

    static List<DashTrack> filterByLanguage(@NonNull String language, List<DashTrack> list) {
        List<DashTrack> filtered = new ArrayList<>();
        for (DashTrack track : list) {
            if (language.equals(track.getLanguage())) {
                filtered.add(track);
            }
        }
        return filtered;
    }

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

    String getRelativeId() {
        return "a" + getAdaptationIndex() + "r" + getRepresentationIndex();
    }

    public int getAdaptationIndex() {
        return adaptationIndex;
    }

    public int getRepresentationIndex() {
        return representationIndex;
    }
}
