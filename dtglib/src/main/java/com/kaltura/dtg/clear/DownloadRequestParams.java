package com.kaltura.dtg.clear;

import android.net.Uri;

import java.util.Map;

/**
 * Created by gilad.nadav on 13/09/2017.
 */

public class DownloadRequestParams {

    public final Uri url;
    public final Map<String, String> headers;

    public DownloadRequestParams(Uri url, Map<String, String> headers) {
        this.url = url;
        this.headers = headers;
    }

    /**
     * PKRequestParams.Adapter allows adapting the request parameters before sending the request
     * to the server.
     */
    public interface Adapter {
        /**
         * Return a potentially modified {@link DownloadRequestParams} object. Note that the input object
         * is immutable -- the implementation can return the same object or create a new one with
         * adapted parameters.
         *
         * @param requestParams Request parameters, as will be sent to the server.
         * @return The input object, or an adapted one.
         */
        DownloadRequestParams adapt(DownloadRequestParams requestParams);

        void updateParams(String playSessionId);
    }
}

