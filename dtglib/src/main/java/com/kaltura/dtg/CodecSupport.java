package com.kaltura.dtg;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.kaltura.dtg.DownloadItem.TrackType;
import com.kaltura.dtg.exoparser.Format;
import com.kaltura.dtg.exoparser.util.MimeTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CodecSupport {

    private static final String TAG = "CodecSupport";

    private static final Set<String> softwareCodecs, hardwareCodecs;

    private static boolean deviceIsEmulator = Build.PRODUCT.equals("sdk") || Build.PRODUCT.startsWith("sdk_") || Build.PRODUCT.endsWith("_sdk");


    static {
        Set<String> hardware = new HashSet<>();
        Set<String> software = new HashSet<>();

        populateCodecSupport(hardware, software);

        softwareCodecs = Collections.unmodifiableSet(software);
        hardwareCodecs = Collections.unmodifiableSet(hardware);
    }

    private static void populateCodecSupport(Set<String> hardware, Set<String> software) {

        ArrayList<MediaCodecInfo> decoders = new ArrayList<>();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
            MediaCodecInfo[] codecInfos = mediaCodecList.getCodecInfos();

            for (MediaCodecInfo codecInfo : codecInfos) {
                if (!codecInfo.isEncoder()) {
                    decoders.add(codecInfo);
                }
            }
        } else {
            for (int i = 0, n = MediaCodecList.getCodecCount(); i < n; i++) {
                final MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
                if (!codecInfo.isEncoder()) {
                    decoders.add(codecInfo);
                }
            }
        }

        for (MediaCodecInfo codecInfo : decoders) {
            final String name = codecInfo.getName();

            final boolean isHardware;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isHardware = codecInfo.isHardwareAccelerated();
            } else {
                isHardware = !name.startsWith("OMX.google.");
            }

            final List<String> supportedCodecs = Arrays.asList(codecInfo.getSupportedTypes());
            final Set<String> set = isHardware ? hardware : software;
            set.addAll(supportedCodecs);
        }
    }

    public static boolean hasDecoder(String codec, boolean isMimeType, boolean allowSoftware) {

        if (deviceIsEmulator) {
            // Emulators have no hardware codecs, but we still need to play.
            allowSoftware = true;
        }

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
