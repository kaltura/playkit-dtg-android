/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kaltura.dtg.exoparser.text.cea;

import android.util.Log;

import com.kaltura.dtg.exoparser.C;
import com.kaltura.dtg.exoparser.extractor.TrackOutput;
import com.kaltura.dtg.exoparser.util.ParsableByteArray;
import com.kaltura.dtg.exoparser.util.Util;

/** Utility methods for handling CEA-608/708 messages. Defined in A/53 Part 4:2009. */
public final class CeaUtil {

  private static final String TAG = "CeaUtil";

  private static final int PAYLOAD_TYPE_CC = 4;
  private static final int COUNTRY_CODE = 0xB5;
  private static final int PROVIDER_CODE_ATSC = 0x31;
  private static final int PROVIDER_CODE_DIRECTV = 0x2F;
  private static final int USER_ID_GA94 = Util.getIntegerCodeForString("GA94");
  private static final int USER_ID_DTG1 = Util.getIntegerCodeForString("DTG1");
  private static final int USER_DATA_TYPE_CODE = 0x3;

  /**
   * Consumes the unescaped content of an SEI NAL unit, writing the content of any CEA-608 messages
   * as samples to all of the provided outputs.
   *
   * @param presentationTimeUs The presentation time in microseconds for any samples.
   * @param seiBuffer The unescaped SEI NAL unit data, excluding the NAL unit start code and type.
   * @param outputs The outputs to which any samples should be written.
   */
  public static void consume(long presentationTimeUs, ParsableByteArray seiBuffer,
      TrackOutput[] outputs) {
    while (seiBuffer.bytesLeft() > 1 /* last byte will be rbsp_trailing_bits */) {
      int payloadType = readNon255TerminatedValue(seiBuffer);
      int payloadSize = readNon255TerminatedValue(seiBuffer);
      int nextPayloadPosition = seiBuffer.getPosition() + payloadSize;
      // Process the payload.
      if (payloadSize == -1 || payloadSize > seiBuffer.bytesLeft()) {
        // This might occur if we're trying to read an encrypted SEI NAL unit.
        Log.w(TAG, "Skipping remainder of malformed SEI NAL unit.");
        nextPayloadPosition = seiBuffer.limit();
      } else if (payloadType == PAYLOAD_TYPE_CC && payloadSize >= 8) {
        int countryCode = seiBuffer.readUnsignedByte();
        int providerCode = seiBuffer.readUnsignedShort();
        int userIdentifier = 0;
        if (providerCode == PROVIDER_CODE_ATSC) {
          userIdentifier = seiBuffer.readInt();
        }
        int userDataTypeCode = seiBuffer.readUnsignedByte();
        if (providerCode == PROVIDER_CODE_DIRECTV) {
          seiBuffer.skipBytes(1); // user_data_length.
        }
        boolean messageIsSupportedCeaCaption =
            countryCode == COUNTRY_CODE
                && (providerCode == PROVIDER_CODE_ATSC || providerCode == PROVIDER_CODE_DIRECTV)
                && userDataTypeCode == USER_DATA_TYPE_CODE;
        if (providerCode == PROVIDER_CODE_ATSC) {
          messageIsSupportedCeaCaption &=
              userIdentifier == USER_ID_GA94 || userIdentifier == USER_ID_DTG1;
        }
        if (messageIsSupportedCeaCaption) {
          // Ignore first three bits: reserved (1) + process_cc_data_flag (1) + zero_bit (1).
          int ccCount = seiBuffer.readUnsignedByte() & 0x1F;
          // Ignore em_data (1)
          seiBuffer.skipBytes(1);
          // Each data packet consists of 24 bits: marker bits (5) + cc_valid (1) + cc_type (2)
          // + cc_data_1 (8) + cc_data_2 (8).
          int sampleLength = ccCount * 3;
          int sampleStartPosition = seiBuffer.getPosition();
          for (TrackOutput output : outputs) {
            seiBuffer.setPosition(sampleStartPosition);
            output.sampleData(seiBuffer, sampleLength);
            output.sampleMetadata(
                presentationTimeUs, C.BUFFER_FLAG_KEY_FRAME, sampleLength, 0, null);
          }
        }
      }
      seiBuffer.setPosition(nextPayloadPosition);
    }
  }

  /**
   * Reads a value from the provided buffer consisting of zero or more 0xFF bytes followed by a
   * terminating byte not equal to 0xFF. The returned value is ((0xFF * N) + T), where N is the
   * number of 0xFF bytes and T is the value of the terminating byte.
   *
   * @param buffer The buffer from which to read the value.
   * @return The read value, or -1 if the end of the buffer is reached before a value is read.
   */
  private static int readNon255TerminatedValue(ParsableByteArray buffer) {
    int b;
    int value = 0;
    do {
      if (buffer.bytesLeft() == 0) {
        return -1;
      }
      b = buffer.readUnsignedByte();
      value += b;
    } while (b == 0xFF);
    return value;
  }

  private CeaUtil() {}

}
