package com.kaltura.dtg.hls;

import android.database.Cursor;

import com.kaltura.dtg.BaseTrack;
import com.kaltura.dtg.DownloadItem;

public class HlsTrack extends BaseTrack {

    HlsTrack(DownloadItem.TrackType type, String language, long bitrate) {
        super(type, language, bitrate);
    }

    HlsTrack(Cursor cursor) {
        super(cursor);
    }


    @Override
    protected void parseExtra(String extra) {

    }

    @Override
    protected String dumpExtra() {
        return null;
    }

    @Override
    protected String getRelativeId() {
        return null;
    }
}
