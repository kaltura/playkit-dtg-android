package com.kaltura.dtg.dash;

import android.database.Cursor;

import com.kaltura.dtg.BaseTrack;
import com.kaltura.dtg.DownloadItemImp;

import java.io.IOException;

public class Factory {
    public static BaseTrack createTrack(Cursor cursor) {
        return new DashTrack(cursor);
    }

    public static DashDownloader createUpdater(DownloadItemImp item) throws IOException {
        return new DashDownloadUpdater(item);
    }
}
