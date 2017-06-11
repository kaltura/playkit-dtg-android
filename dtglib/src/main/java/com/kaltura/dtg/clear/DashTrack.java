package com.kaltura.dtg.clear;

import android.content.ContentValues;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.text.TextUtils;
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
    static final String[] REQUIRED_DB_FIELDS =
            {Database.COL_TRACK_ID, Database.COL_TRACK_TYPE, Database.COL_TRACK_LANGUAGE, Database.COL_TRACK_BITRATE, Database.COL_TRACK_EXTRA};
    private static final String ORIGINAL_ADAPTATION_SET_INDEX = "originalAdaptationSetIndex";
    private static final String ORIGINAL_REPRESENTATION_INDEX = "originalRepresentationIndex";
    private static final String TAG = "DashTrack";
    private int adaptationIndex;
    private int representationIndex;
    private DownloadItem.TrackType type;
    private String language;
    private long bitrate;
    private int width;
    private int height;

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

    static List<DashTrack> filterByLanguage(@NonNull String language, List<DashTrack> list) {
        List<DashTrack> filtered = new ArrayList<>();
        for (DashTrack track : list) {
            if (language.equals(track.getLanguage())) {
                filtered.add(track);
            }
        }
        return filtered;
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

    int getAdaptationIndex() {
        return adaptationIndex;
    }

    int getRepresentationIndex() {
        return representationIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DashTrack dashTrack = (DashTrack) o;

        if (adaptationIndex != dashTrack.adaptationIndex) return false;
        if (representationIndex != dashTrack.representationIndex) return false;
        if (bitrate != dashTrack.bitrate) return false;
        if (width != dashTrack.width) return false;
        if (height != dashTrack.height) return false;
        if (type != dashTrack.type) return false;
        return TextUtils.equals(language, dashTrack.language);
    }

    @Override
    public int hashCode() {
        int result = adaptationIndex;
        result = 31 * result + representationIndex;
        if (type != null) {
            result = 31 * result + type.hashCode();
        }
        
        if (language != null) {
            result = 31 * result + language.hashCode();
        }
        
        result = 31 * result + (int) (bitrate ^ (bitrate >>> 32));
        result = 31 * result + width;
        result = 31 * result + height;
        return result;
    }

    @Override
    public String toString() {
        return "DashTrack{" +
                "adaptationIndex=" + adaptationIndex +
                ", representationIndex=" + representationIndex +
                ", type=" + type +
                ", language='" + language + '\'' +
                ", bitrate=" + bitrate +
                ", resolution=" + width + "x" + height +
                '}';
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getWidth() {
        return width;
    }

    void setWidth(int width) {
        this.width = width;
    }

    void setHeight(int height) {
        this.height = height;
    }
}
