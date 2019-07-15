/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kaltura.dtg.exoparser.mediacodec;

import android.annotation.TargetApi;
import android.graphics.Point;
import android.media.MediaCodec;
import android.media.MediaCodecInfo.AudioCapabilities;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecInfo.VideoCapabilities;
import androidx.annotation.Nullable;
import android.util.Log;
import android.util.Pair;

import com.kaltura.dtg.exoparser.Format;
import com.kaltura.dtg.exoparser.util.Assertions;
import com.kaltura.dtg.exoparser.util.MimeTypes;
import com.kaltura.dtg.exoparser.util.Util;

/**
 * Information about a {@link MediaCodec} for a given mime type.
 */
@TargetApi(16)
public final class MediaCodecInfo {

  private static final String TAG = "MediaCodecInfo";

  /**
   * The value returned by {@link #getMaxSupportedInstances()} if the upper bound on the maximum
   * number of supported instances is unknown.
   */
  private static final int MAX_SUPPORTED_INSTANCES_UNKNOWN = -1;

  /**
   * The name of the decoder.
   * <p>
   * May be passed to {@link MediaCodec#createByCodecName(String)} to create an instance of the
   * decoder.
   */
  public final String name;

  /** The MIME type handled by the codec, or {@code null} if this is a passthrough codec. */
  private final @Nullable String mimeType;

  /**
   * The capabilities of the decoder, like the profiles/levels it supports, or {@code null} if this
   * is a passthrough codec.
   */
  private final @Nullable CodecCapabilities capabilities;

  /**
   * Whether the decoder supports seamless resolution switches.
   *
   * @see CodecCapabilities#isFeatureSupported(String)
   * @see CodecCapabilities#FEATURE_AdaptivePlayback
   */
  private final boolean adaptive;

  /**
   * Whether the decoder supports tunneling.
   *
   * @see CodecCapabilities#isFeatureSupported(String)
   * @see CodecCapabilities#FEATURE_TunneledPlayback
   */
  private final boolean tunneling;

  /**
   * Whether the decoder is secure.
   *
   * @see CodecCapabilities#isFeatureSupported(String)
   * @see CodecCapabilities#FEATURE_SecurePlayback
   */
  private final boolean secure;

  /** Whether this instance describes a passthrough codec. */
  private final boolean passthrough;

  /**
   * Creates an instance representing an audio passthrough decoder.
   *
   * @param name The name of the {@link MediaCodec}.
   * @return The created instance.
   */
  public static MediaCodecInfo newPassthroughInstance(String name) {
    return new MediaCodecInfo(
        name,
        /* mimeType= */ null,
        /* capabilities= */ null,
        /* passthrough= */ true,
        /* forceDisableAdaptive= */ false,
        /* forceSecure= */ false);
  }

  /**
   * Creates an instance.
   *
   * @param name The name of the {@link MediaCodec}.
   * @param mimeType A mime type supported by the {@link MediaCodec}.
   * @param capabilities The capabilities of the {@link MediaCodec} for the specified mime type.
   * @return The created instance.
   */
  public static MediaCodecInfo newInstance(String name, String mimeType,
      CodecCapabilities capabilities) {
    return new MediaCodecInfo(
        name,
        mimeType,
        capabilities,
        /* passthrough= */ false,
        /* forceDisableAdaptive= */ false,
        /* forceSecure= */ false);
  }

  /**
   * Creates an instance.
   *
   * @param name The name of the {@link MediaCodec}.
   * @param mimeType A mime type supported by the {@link MediaCodec}.
   * @param capabilities The capabilities of the {@link MediaCodec} for the specified mime type.
   * @param forceDisableAdaptive Whether {@link #adaptive} should be forced to {@code false}.
   * @param forceSecure Whether {@link #secure} should be forced to {@code true}.
   * @return The created instance.
   */
  public static MediaCodecInfo newInstance(
      String name,
      String mimeType,
      CodecCapabilities capabilities,
      boolean forceDisableAdaptive,
      boolean forceSecure) {
    return new MediaCodecInfo(
        name, mimeType, capabilities, /* passthrough= */ false, forceDisableAdaptive, forceSecure);
  }

  private MediaCodecInfo(
      String name,
      @Nullable String mimeType,
      @Nullable CodecCapabilities capabilities,
      boolean passthrough,
      boolean forceDisableAdaptive,
      boolean forceSecure) {
    this.name = Assertions.checkNotNull(name);
    this.mimeType = mimeType;
    this.capabilities = capabilities;
    this.passthrough = passthrough;
    adaptive = !forceDisableAdaptive && capabilities != null && isAdaptive(capabilities);
    tunneling = capabilities != null && isTunneling(capabilities);
    secure = forceSecure || (capabilities != null && isSecure(capabilities));
  }

  /**
   * The profile levels supported by the decoder.
   *
   * @return The profile levels supported by the decoder.
   */
  public CodecProfileLevel[] getProfileLevels() {
    return capabilities == null || capabilities.profileLevels == null ? new CodecProfileLevel[0]
        : capabilities.profileLevels;
  }

  /**
   * Returns an upper bound on the maximum number of supported instances, or {@link
   * #MAX_SUPPORTED_INSTANCES_UNKNOWN} if unknown. Applications should not expect to operate more
   * instances than the returned maximum.
   *
   * @see CodecCapabilities#getMaxSupportedInstances()
   */
  public int getMaxSupportedInstances() {
    return (Util.SDK_INT < 23 || capabilities == null)
        ? MAX_SUPPORTED_INSTANCES_UNKNOWN
        : getMaxSupportedInstancesV23(capabilities);
  }

  /**
   * Whether the decoder supports the given {@code codec}. If there is insufficient information to
   * decide, returns true.
   *
   * @param codec Codec string as defined in RFC 6381.
   * @return True if the given codec is supported by the decoder.
   */
  public boolean isCodecSupported(String codec) {
    if (codec == null || mimeType == null) {
      return true;
    }
    String codecMimeType = MimeTypes.getMediaMimeType(codec);
    if (codecMimeType == null) {
      return true;
    }
    if (!mimeType.equals(codecMimeType)) {
      logNoSupport("codec.mime " + codec + ", " + codecMimeType);
      return false;
    }
    Pair<Integer, Integer> codecProfileAndLevel = MediaCodecUtil.getCodecProfileAndLevel(codec);
    if (codecProfileAndLevel == null) {
      // If we don't know any better, we assume that the profile and level are supported.
      return true;
    }
    for (CodecProfileLevel capabilities : getProfileLevels()) {
      if (capabilities.profile == codecProfileAndLevel.first
          && capabilities.level >= codecProfileAndLevel.second) {
        return true;
      }
    }
    logNoSupport("codec.profileLevel, " + codec + ", " + codecMimeType);
    return false;
  }

  /**
   * Whether the decoder supports video with a given width, height and frame rate.
   * <p>
   * Must not be called if the device SDK version is less than 21.
   *
   * @param width Width in pixels.
   * @param height Height in pixels.
   * @param frameRate Optional frame rate in frames per second. Ignored if set to
   *     {@link Format#NO_VALUE} or any value less than or equal to 0.
   * @return Whether the decoder supports video with the given width, height and frame rate.
   */
  @TargetApi(21)
  public boolean isVideoSizeAndRateSupportedV21(int width, int height, double frameRate) {
    if (capabilities == null) {
      logNoSupport("sizeAndRate.caps");
      return false;
    }
    VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities();
    if (videoCapabilities == null) {
      logNoSupport("sizeAndRate.vCaps");
      return false;
    }
    if (!areSizeAndRateSupportedV21(videoCapabilities, width, height, frameRate)) {
      // Capabilities are known to be inaccurately reported for vertical resolutions on some devices
      // (b/31387661). If the video is vertical and the capabilities indicate support if the width
      // and height are swapped, we assume that the vertical resolution is also supported.
      if (width >= height
          || !areSizeAndRateSupportedV21(videoCapabilities, height, width, frameRate)) {
        logNoSupport("sizeAndRate.support, " + width + "x" + height + "x" + frameRate);
        return false;
      }
      logAssumedSupport("sizeAndRate.rotated, " + width + "x" + height + "x" + frameRate);
    }
    return true;
  }

  /**
   * Returns the smallest video size greater than or equal to a specified size that also satisfies
   * the {@link MediaCodec}'s width and height alignment requirements.
   * <p>
   * Must not be called if the device SDK version is less than 21.
   *
   * @param width Width in pixels.
   * @param height Height in pixels.
   * @return The smallest video size greater than or equal to the specified size that also satisfies
   *     the {@link MediaCodec}'s width and height alignment requirements, or null if not a video
   *     codec.
   */
  @TargetApi(21)
  public Point alignVideoSizeV21(int width, int height) {
    if (capabilities == null) {
      logNoSupport("align.caps");
      return null;
    }
    VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities();
    if (videoCapabilities == null) {
      logNoSupport("align.vCaps");
      return null;
    }
    int widthAlignment = videoCapabilities.getWidthAlignment();
    int heightAlignment = videoCapabilities.getHeightAlignment();
    return new Point(Util.ceilDivide(width, widthAlignment) * widthAlignment,
        Util.ceilDivide(height, heightAlignment) * heightAlignment);
  }

  /**
   * Whether the decoder supports audio with a given sample rate.
   * <p>
   * Must not be called if the device SDK version is less than 21.
   *
   * @param sampleRate The sample rate in Hz.
   * @return Whether the decoder supports audio with the given sample rate.
   */
  @TargetApi(21)
  public boolean isAudioSampleRateSupportedV21(int sampleRate) {
    if (capabilities == null) {
      logNoSupport("sampleRate.caps");
      return false;
    }
    AudioCapabilities audioCapabilities = capabilities.getAudioCapabilities();
    if (audioCapabilities == null) {
      logNoSupport("sampleRate.aCaps");
      return false;
    }
    if (!audioCapabilities.isSampleRateSupported(sampleRate)) {
      logNoSupport("sampleRate.support, " + sampleRate);
      return false;
    }
    return true;
  }

  /**
   * Whether the decoder supports audio with a given channel count.
   * <p>
   * Must not be called if the device SDK version is less than 21.
   *
   * @param channelCount The channel count.
   * @return Whether the decoder supports audio with the given channel count.
   */
  @TargetApi(21)
  public boolean isAudioChannelCountSupportedV21(int channelCount) {
    if (capabilities == null) {
      logNoSupport("channelCount.caps");
      return false;
    }
    AudioCapabilities audioCapabilities = capabilities.getAudioCapabilities();
    if (audioCapabilities == null) {
      logNoSupport("channelCount.aCaps");
      return false;
    }
    int maxInputChannelCount = adjustMaxInputChannelCount(name, mimeType,
        audioCapabilities.getMaxInputChannelCount());
    if (maxInputChannelCount < channelCount) {
      logNoSupport("channelCount.support, " + channelCount);
      return false;
    }
    return true;
  }

  private void logNoSupport(String message) {
    Log.d(TAG, "NoSupport [" + message + "] [" + name + ", " + mimeType + "] ["
        + Util.DEVICE_DEBUG_INFO + "]");
  }

  private void logAssumedSupport(String message) {
    Log.d(TAG, "AssumedSupport [" + message + "] [" + name + ", " + mimeType + "] ["
        + Util.DEVICE_DEBUG_INFO + "]");
  }

  private static int adjustMaxInputChannelCount(String name, String mimeType, int maxChannelCount) {
    if (maxChannelCount > 1 || (Util.SDK_INT >= 26 && maxChannelCount > 0)) {
      // The maximum channel count looks like it's been set correctly.
      return maxChannelCount;
    }
    if (MimeTypes.AUDIO_MPEG.equals(mimeType)
        || MimeTypes.AUDIO_AMR_NB.equals(mimeType)
        || MimeTypes.AUDIO_AMR_WB.equals(mimeType)
        || MimeTypes.AUDIO_AAC.equals(mimeType)
        || MimeTypes.AUDIO_VORBIS.equals(mimeType)
        || MimeTypes.AUDIO_OPUS.equals(mimeType)
        || MimeTypes.AUDIO_RAW.equals(mimeType)
        || MimeTypes.AUDIO_FLAC.equals(mimeType)
        || MimeTypes.AUDIO_ALAW.equals(mimeType)
        || MimeTypes.AUDIO_MLAW.equals(mimeType)
        || MimeTypes.AUDIO_MSGSM.equals(mimeType)) {
      // Platform code should have set a default.
      return maxChannelCount;
    }
    // The maximum channel count looks incorrect. Adjust it to an assumed default.
    int assumedMaxChannelCount;
    if (MimeTypes.AUDIO_AC3.equals(mimeType)) {
      assumedMaxChannelCount = 6;
    } else if (MimeTypes.AUDIO_E_AC3.equals(mimeType)) {
      assumedMaxChannelCount = 16;
    } else {
      // Default to the platform limit, which is 30.
      assumedMaxChannelCount = 30;
    }
    Log.w(TAG, "AssumedMaxChannelAdjustment: " + name + ", [" + maxChannelCount + " to "
        + assumedMaxChannelCount + "]");
    return assumedMaxChannelCount;
  }

  private static boolean isAdaptive(CodecCapabilities capabilities) {
    return Util.SDK_INT >= 19 && isAdaptiveV19(capabilities);
  }

  @TargetApi(19)
  private static boolean isAdaptiveV19(CodecCapabilities capabilities) {
    return capabilities.isFeatureSupported(CodecCapabilities.FEATURE_AdaptivePlayback);
  }

  private static boolean isTunneling(CodecCapabilities capabilities) {
    return Util.SDK_INT >= 21 && isTunnelingV21(capabilities);
  }

  @TargetApi(21)
  private static boolean isTunnelingV21(CodecCapabilities capabilities) {
    return capabilities.isFeatureSupported(CodecCapabilities.FEATURE_TunneledPlayback);
  }

  private static boolean isSecure(CodecCapabilities capabilities) {
    return Util.SDK_INT >= 21 && isSecureV21(capabilities);
  }

  @TargetApi(21)
  private static boolean isSecureV21(CodecCapabilities capabilities) {
    return capabilities.isFeatureSupported(CodecCapabilities.FEATURE_SecurePlayback);
  }

  @TargetApi(21)
  private static boolean areSizeAndRateSupportedV21(VideoCapabilities capabilities, int width,
      int height, double frameRate) {
    return frameRate == Format.NO_VALUE || frameRate <= 0
        ? capabilities.isSizeSupported(width, height)
        : capabilities.areSizeAndRateSupported(width, height, frameRate);
  }

  @TargetApi(23)
  private static int getMaxSupportedInstancesV23(CodecCapabilities capabilities) {
    return capabilities.getMaxSupportedInstances();
  }
}
