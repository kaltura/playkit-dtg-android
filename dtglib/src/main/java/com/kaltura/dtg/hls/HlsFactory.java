package com.kaltura.dtg.hls;

import android.database.Cursor;

import com.kaltura.dtg.BaseAbrDownloader;
import com.kaltura.dtg.BaseTrack;
import com.kaltura.dtg.DownloadItemImp;

import java.io.IOException;

public class HlsFactory {
    public static BaseTrack createTrack(Cursor cursor) {
        // FIXME: 05/07/2018
        return null; //new HlsTrack(cursor);
    }

    public static BaseAbrDownloader createUpdater(DownloadItemImp item) throws IOException {
        // FIXME: 05/07/2018
        return null; //new HlsDownloadUpdater(item);
    }
}
