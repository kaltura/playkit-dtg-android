package com.kaltura.dtg;

import android.net.Uri;
import android.util.Base64;

import static com.kaltura.dtg.ContentManager.CLIENT_TAG;


public class KalturaDownloadRequestAdapter implements DownloadRequestParams.Adapter {

    private static final String PLAYBACK_TYPE_PARAM = "playbackType";
    private static final String CLIENT_TAG_PARAM = "clientTag";
    private static final String REFERRER_PARAM = "referrer";
    private static final String PLAY_SESSION_ID_PARAM = "playSessionId";
    private final String applicationName;
    private String playSessionId;

    public KalturaDownloadRequestAdapter(String playSessionId, String applicationName) {
        this.playSessionId = playSessionId;
        this.applicationName = applicationName;
    }

    private static String toBase64(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        return Base64.encodeToString(data, Base64.NO_WRAP);
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