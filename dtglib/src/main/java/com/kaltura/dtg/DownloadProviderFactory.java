package com.kaltura.dtg;

import android.content.Context;

/**
 * Created by noamt on 15/06/2016.
 */
class DownloadProviderFactory {
    // TODO: more providers, some logic
    static DownloadProvider getProvider(Context context) {
        return com.kaltura.dtg.clear.Factory.getProvider(context);
    }
}
