package com.kaltura.dtg;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.kaltura.dtg.exoparser.Format;
import com.kaltura.dtg.exoparser.util.MimeTypes;
import com.kaltura.dtg.DownloadItem.TrackType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class CodecSupport {

    private static final String TAG = "CodecSupport";

    private static final Set<String> softwareCodecs, hardwareCodecs;

    static {
        Set<String> hardware = new HashSet<>();
        Set<String> software = new HashSet<>();

        populateCodecSupport(hardware, software);

        softwareCodecs = Collections.unmodifiableSet(software);
        hardwareCodecs = Collections.unmodifiableSet(hardware);
    }

    private static void populateCodecSupport(Set<String> hardware, Set<String> software) {
        for (int i = 0, n = MediaCodecList.getCodecCount(); i < n; i++) {
            final MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            final String name = codecInfo.getName();
            if (codecInfo.isEncoder()) {
                continue;
            }

            final boolean isHardware;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isHardware = codecInfo.isHardwareAccelerated();
            } else {
                isHardware = !name.startsWith("OMX.google.");
            }

            final Set<String> set = isHardware ? hardware : software;
            set.addAll(Arrays.asList(codecInfo.getSupportedTypes()));
        }
    }

    public static boolean hasDecoder(String codec, boolean isMimeType, boolean allowSoftware) {
        final String mimeType = isMimeType ? codec : MimeTypes.getMediaMimeType(codec);
        if (hardwareCodecs.contains(mimeType)) {
            return true;
        }

        return allowSoftware && softwareCodecs.contains(mimeType);
    }

    public static boolean isFormatSupported(@NonNull Format format, @Nullable TrackType type) {

        if (type == TrackType.TEXT) {
            return true;    // always supported
        }

        if (format.codecs == null) {
            Log.w(TAG, "isFormatSupported: codecs==null, assuming supported");
            return true;
        }

        if (type == null) {
            // type==null: HLS muxed track with a <video,audio> tuple
            final String[] split = TextUtils.split(format.codecs, ",");
            boolean result = true;
            switch (split.length) {
                case 0: return false;
                case 2: result = hasDecoder(split[1], false, true);
                // fallthrough
                case 1: result &= hasDecoder(split[0], false, true);
            }
            return result;

        } else {
            return hasDecoder(format.codecs, false, true);
        }
    }
}
