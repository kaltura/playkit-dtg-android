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

import android.media.MediaCodec;

/**
 * Selector of {@link MediaCodec} instances.
 */
public interface MediaCodecSelector {

  /**
   * Default implementation of {@link MediaCodecSelector}.
   */
  MediaCodecSelector DEFAULT = new MediaCodecSelector() {

    @Override
    public MediaCodecInfo getDecoderInfo(String mimeType, boolean requiresSecureDecoder)
        throws MediaCodecUtil.DecoderQueryException {
      return MediaCodecUtil.getDecoderInfo(mimeType, requiresSecureDecoder);
    }

    @Override
    public MediaCodecInfo getPassthroughDecoderInfo() {
      return MediaCodecUtil.getPassthroughDecoderInfo();
    }

  };

  /**
   * Selects a decoder to instantiate for a given mime type.
   *
   * @param mimeType The mime type for which a decoder is required.
   * @param requiresSecureDecoder Whether a secure decoder is required.
   * @return A {@link MediaCodecInfo} describing the decoder, or null if no suitable decoder exists.
   * @throws MediaCodecUtil.DecoderQueryException Thrown if there was an error querying decoders.
   */
  MediaCodecInfo getDecoderInfo(String mimeType, boolean requiresSecureDecoder)
      throws MediaCodecUtil.DecoderQueryException;

  /**
   * Selects a decoder to instantiate for audio passthrough.
   *
   * @return A {@link MediaCodecInfo} describing the decoder, or null if no suitable decoder exists.
   * @throws MediaCodecUtil.DecoderQueryException Thrown if there was an error querying decoders.
   */
  MediaCodecInfo getPassthroughDecoderInfo() throws MediaCodecUtil.DecoderQueryException;

}
