package com.kaltura.dtg.clear;

import android.net.Uri;

import static com.kaltura.dtg.Utils.toBase64;
import static com.kaltura.dtg.clear.DefaultDownloadService.CLIENT_TAG;


public class KalturaDownloadRequestAdapter implements DownloadRequestParams.Adapter {

    private final String applicationName;
    private String playSessionId;

    public KalturaDownloadRequestAdapter(String playSessionId, String applicationName) {
        this.playSessionId = playSessionId;
        this.applicationName = applicationName;
    }

    @Override
    public DownloadRequestParams adapt(DownloadRequestParams requestParams) {
        Uri url = requestParams.url;

        if (url.getPath().contains("/playManifest/")) {
            Uri alt = url.buildUpon()
                    .appendQueryParameter("playbackType", "offline")
                    .appendQueryParameter("clientTag", CLIENT_TAG)
                    .appendQueryParameter("referrer", toBase64(applicationName.getBytes()))
                    .appendQueryParameter("playSessionId", playSessionId)
                    .build();

            String lastPathSegment = requestParams.url.getLastPathSegment();
            if (lastPathSegment.endsWith(".wvm")) {
                // in old android device it will not play wvc if url is not ended in wvm
                alt = alt.buildUpon().appendQueryParameter("name", lastPathSegment).build();
            }
            return new DownloadRequestParams(alt, requestParams.headers);
        }

        return requestParams;
    }

    @Override
    public void updateParams(String playSessionId) {
        this.playSessionId = playSessionId;
    }
}