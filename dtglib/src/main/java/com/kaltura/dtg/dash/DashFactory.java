package com.kaltura.dtg.dash;

import android.database.Cursor;

import com.kaltura.dtg.BaseAbrDownloader;
import com.kaltura.dtg.BaseTrack;
import com.kaltura.dtg.DownloadItemImp;

import java.io.IOException;

public class DashFactory {
    public static BaseTrack createTrack(Cursor cursor) {
        return new DashTrack(cursor);
    }

    public static BaseAbrDownloader createUpdater(DownloadItemImp item) throws IOException {
        return new DashDownloadUpdater(item);
    }
}
