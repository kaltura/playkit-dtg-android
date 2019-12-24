package com.kaltura.dtg;

import android.text.TextUtils;

public enum AssetFormat {
    dash, hls, mp4, wvm, mp3, invalid;

    static final AssetFormat[] valid = {dash, hls, mp4, wvm, mp3};

    public String extension() {
        switch (this) {
            case dash:
                return ".mpd";
            case hls:
                return ".m3u8";
            case mp4:
                return ".mp4";
            case wvm:
                return ".wvm";
            case mp3:
                return ".mp3";
        }
        throw new IllegalStateException();
    }

    public static AssetFormat byFilename(String filename) {
        if (!TextUtils.isEmpty(filename)) {
            for (AssetFormat assetFormat : valid) {
                if (filename.endsWith(assetFormat.extension())) {
                    return assetFormat;
                }
            }
        }
        return invalid;
    }

    boolean isAbr() {
        return this == dash || this == hls;
    }
}
