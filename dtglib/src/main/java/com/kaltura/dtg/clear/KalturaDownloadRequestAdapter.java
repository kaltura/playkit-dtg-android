package com.kaltura.dtg.clear;

import android.net.Uri;

import static com.kaltura.dtg.ContentManager.CLIENT_TAG;
import static com.kaltura.dtg.Utils.toBase64;


public class KalturaDownloadRequestAdapter implements DownloadRequestParams.Adapter {

    public static final String PLAYBACK_TYPE_PARAM = "playbackType";
    public static final String CLIENT_TAG_PARAM = "clientTag";
    public static final String REFERRER_PARAM = "referrer";
    public static final String PLAY_SESSION_ID_PARAM = "playSessionId";
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
                    .appendQueryParameter(PLAYBACK_TYPE_PARAM, "offline")
                    .appendQueryParameter(CLIENT_TAG_PARAM, CLIENT_TAG)
                    .appendQueryParameter(REFERRER_PARAM, toBase64(applicationName.getBytes()))
                    .appendQueryParameter(PLAY_SESSION_ID_PARAM, playSessionId)
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