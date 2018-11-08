package com.kaltura.dtg.imp;

import android.database.Cursor;

import com.kaltura.dtg.parser.Format;
import com.kaltura.dtg.DownloadItem;

import org.json.JSONException;
import org.json.JSONObject;

class DashTrack extends BaseTrack {
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

    DashTrack(Cursor cursor) {
        super(cursor);
    }

    @Override
    void parseExtra(JSONObject jsonExtra) {
        adaptationIndex = jsonExtra.optInt(EXTRA_ADAPTATION_INDEX, 0);
        representationIndex = jsonExtra.optInt(EXTRA_REPRESENTATION_INDEX, 0);
    }

    @Override
    void dumpExtra(JSONObject jsonExtra) throws JSONException {
        jsonExtra.put(EXTRA_ADAPTATION_INDEX, adaptationIndex)
                .put(EXTRA_REPRESENTATION_INDEX, representationIndex);
    }

    @Override
    String getRelativeId() {
        return "a" + adaptationIndex + "r" + representationIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        DashTrack dashTrack = (DashTrack) o;
        return adaptationIndex == dashTrack.adaptationIndex &&
                representationIndex == dashTrack.representationIndex;
    }

    @Override
    public int hashCode() {
        return Utils.hash(super.hashCode(), adaptationIndex, representationIndex);
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
