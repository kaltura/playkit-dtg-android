package com.kaltura.dtg.clear;

import android.content.Context;

import com.kaltura.dtg.DownloadProvider;

/**
 * Created by noamt on 13/06/2016.
 */
public class Factory {
    public static DownloadProvider getProvider(Context context) {
        return new ClearDownloadProvider(context);
    }
}
