package com.kaltura.dtg.hls;

import android.database.Cursor;

import com.kaltura.dtg.BaseAbrDownloader;
import com.kaltura.dtg.BaseTrack;
import com.kaltura.dtg.DownloadItemImp;

import java.io.IOException;

public class HlsFactory {
    public static BaseTrack newTrack(Cursor cursor) {
        return new HlsAsset.Track(cursor);
    }

    public static BaseAbrDownloader newUpdater(DownloadItemImp item) throws IOException {
        return new HlsDownloader(item).initForUpdate();
    }
}
