package com.kaltura.dtg.dash;

import android.database.Cursor;

import com.kaltura.android.exoplayer.chunk.Format;
import com.kaltura.dtg.BaseTrack;
import com.kaltura.dtg.DownloadItem;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by noamt on 13/09/2016.
 */
public class DashTrack extends BaseTrack {
    private static final String TAG = "DashTrack";

    private static final String EXTRA_ADAPTATION_INDEX = "originalAdaptationSetIndex";
    private static final String EXTRA_REPRESENTATION_INDEX = "originalRepresentationIndex";

    int adaptationIndex;
    int representationIndex;

    DashTrack(DownloadItem.TrackType type, Format format, int adaptationIndex, int representationIndex) {
        super(type, format);
        this.adaptationIndex = adaptationIndex;
        this.representationIndex = representationIndex;
    }

    public DashTrack(Cursor cursor) {
        super(cursor);
    }

    @Override
    protected void parseExtra(JSONObject jsonExtra) {
        adaptationIndex = jsonExtra.optInt(EXTRA_ADAPTATION_INDEX, 0);
        representationIndex = jsonExtra.optInt(EXTRA_REPRESENTATION_INDEX, 0);
    }

    @Override
    protected void dumpExtra(JSONObject jsonExtra) throws JSONException {
        jsonExtra.put(EXTRA_ADAPTATION_INDEX, adaptationIndex)
                .put(EXTRA_REPRESENTATION_INDEX, representationIndex);
    }

    @Override
    protected String getRelativeId() {
        return "a" + adaptationIndex + "r" + representationIndex;
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

    void setHeight(int height) {
        this.height = height;
    }

    void setWidth(int width) {
        this.width = width;
    }
}
