package com.kaltura.dtg.dash;

import android.database.Cursor;
import android.util.Log;

import com.kaltura.android.exoplayer.chunk.Format;
import com.kaltura.dtg.BaseTrack;
import com.kaltura.dtg.DownloadItem;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by noamt on 13/09/2016.
 */
public class DashTrack extends BaseTrack {
    private static final String ORIGINAL_ADAPTATION_SET_INDEX = "originalAdaptationSetIndex";
    private static final String ORIGINAL_REPRESENTATION_INDEX = "originalRepresentationIndex";
    private static final String TAG = "DashTrack";
    private int adaptationIndex;
    private int representationIndex;

    DashTrack(DownloadItem.TrackType type, Format format, int adaptationIndex, int representationIndex) {
        super(type, format);
        this.adaptationIndex = adaptationIndex;
        this.representationIndex = representationIndex;
    }

    public DashTrack(Cursor cursor) {
        super(cursor);
    }

    @Override
    protected void parseExtra(String extra) {
        JSONObject jsonExtra;
        try {
            jsonExtra = new JSONObject(extra);
            adaptationIndex = jsonExtra.optInt(ORIGINAL_ADAPTATION_SET_INDEX, 0);
            representationIndex = jsonExtra.optInt(ORIGINAL_REPRESENTATION_INDEX, 0);
        } catch (JSONException e) {
            Log.e(TAG, "Can't parse track extra", e);
        }
    }

    @Override
    protected String getRelativeId() {
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
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        int result = adaptationIndex;
        result = 31 * result + representationIndex;
        result = 31 * result + super.hashCode();
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
    protected String dumpExtra() {
        try {
            return new JSONObject()
                    .put(DashTrack.ORIGINAL_ADAPTATION_SET_INDEX, getAdaptationIndex())
                    .put(DashTrack.ORIGINAL_REPRESENTATION_INDEX, getRepresentationIndex())
                    .toString();
        } catch (JSONException e) {
            Log.e(DashTrack.TAG, "Failed converting to JSON");
            return null;
        }
    }

    void setHeight(int height) {
        this.height = height;
    }

    void setWidth(int width) {
        this.width = width;
    }
}
