package com.kaltura.dtg;

public enum AssetFormat {
    mp4, wvm, hls, dash;

    public String extension() {
        switch (this) {
            case mp4: return ".mp4";
            case wvm: return ".wvm";
            case hls: return ".m3u8";
            case dash: return ".mpd";
        }
        throw new IllegalStateException();
    }
}
