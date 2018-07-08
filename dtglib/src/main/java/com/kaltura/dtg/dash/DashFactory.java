package com.kaltura.dtg.dash;

import android.database.Cursor;

import com.kaltura.dtg.AbrDownloader;
import com.kaltura.dtg.BaseTrack;
import com.kaltura.dtg.DownloadItemImp;

import java.io.IOException;

public class DashFactory {
    public static BaseTrack newTrack(Cursor cursor) {
        return new DashTrack(cursor);
    }

    public static AbrDownloader newUpdater(DownloadItemImp item) throws IOException {
        return new DashDownloader(item).initForUpdate();
    }
}
