package com.kaltura.dtg.hls;

import android.database.Cursor;

import com.kaltura.dtg.AbrDownloader;
import com.kaltura.dtg.BaseTrack;
import com.kaltura.dtg.DownloadItemImp;

import java.io.IOException;

public class HlsFactory {
    public static BaseTrack newTrack(Cursor cursor) {
        return new HlsAsset.Track(cursor);
    }

    public static AbrDownloader newUpdater(DownloadItemImp item) throws IOException {
        return new HlsDownloader(item).initForUpdate();
    }
}
