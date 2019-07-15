/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.kaltura.dtg.exoparser.audio;

import androidx.annotation.Nullable;

import com.kaltura.dtg.exoparser.C;
import com.kaltura.dtg.exoparser.Format;
import com.kaltura.dtg.exoparser.util.Assertions;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * An {@link AudioProcessor} that uses the Sonic library to modify audio speed/pitch/sample rate.
 */
public final class SonicAudioProcessor implements AudioProcessor {

  /**
   * Indicates that the output sample rate should be the same as the input.
   */
  private static final int SAMPLE_RATE_NO_CHANGE = -1;

  /**
   * The threshold below which the difference between two pitch/speed factors is negligible.
   */
  private static final float CLOSE_THRESHOLD = 0.01f;

  private int channelCount;
  private int sampleRateHz;
  private float speed;
  private float pitch;
  private int outputSampleRateHz;
  private int pendingOutputSampleRateHz;

  private @Nullable Sonic sonic;
  private ByteBuffer buffer;
  private ShortBuffer shortBuffer;
  private ByteBuffer outputBuffer;
  private long inputBytes;
  private long outputBytes;
  private boolean inputEnded;

  @Override
  public boolean configure(int sampleRateHz, int channelCount, @C.Encoding int encoding)
      throws UnhandledFormatException {
    if (encoding != C.ENCODING_PCM_16BIT) {
      throw new UnhandledFormatException(sampleRateHz, channelCount, encoding);
    }
    int outputSampleRateHz = pendingOutputSampleRateHz == SAMPLE_RATE_NO_CHANGE
        ? sampleRateHz : pendingOutputSampleRateHz;
    if (this.sampleRateHz == sampleRateHz && this.channelCount == channelCount
        && this.outputSampleRateHz == outputSampleRateHz) {
      return false;
    }
    this.sampleRateHz = sampleRateHz;
    this.channelCount = channelCount;
    this.outputSampleRateHz = outputSampleRateHz;
    sonic = null;
    return true;
  }

  @Override
  public boolean isActive() {
    return sampleRateHz != Format.NO_VALUE
        && (Math.abs(speed - 1f) >= CLOSE_THRESHOLD
            || Math.abs(pitch - 1f) >= CLOSE_THRESHOLD
            || outputSampleRateHz != sampleRateHz);
  }

  @Override
  public int getOutputChannelCount() {
    return channelCount;
  }

  @Override
  public int getOutputEncoding() {
    return C.ENCODING_PCM_16BIT;
  }

  @Override
  public int getOutputSampleRateHz() {
    return outputSampleRateHz;
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    Assertions.checkState(sonic != null);
    if (inputBuffer.hasRemaining()) {
      ShortBuffer shortBuffer = inputBuffer.asShortBuffer();
      int inputSize = inputBuffer.remaining();
      inputBytes += inputSize;
      sonic.queueInput(shortBuffer);
      inputBuffer.position(inputBuffer.position() + inputSize);
    }
    int outputSize = sonic.getFramesAvailable() * channelCount * 2;
    if (outputSize > 0) {
      if (buffer.capacity() < outputSize) {
        buffer = ByteBuffer.allocateDirect(outputSize).order(ByteOrder.nativeOrder());
        shortBuffer = buffer.asShortBuffer();
      } else {
        buffer.clear();
        shortBuffer.clear();
      }
      sonic.getOutput(shortBuffer);
      outputBytes += outputSize;
      buffer.limit(outputSize);
      outputBuffer = buffer;
    }
  }

  @Override
  public void queueEndOfStream() {
    Assertions.checkState(sonic != null);
    sonic.queueEndOfStream();
    inputEnded = true;
  }

  @Override
  public ByteBuffer getOutput() {
    ByteBuffer outputBuffer = this.outputBuffer;
    this.outputBuffer = EMPTY_BUFFER;
    return outputBuffer;
  }

  @Override
  public boolean isEnded() {
    return inputEnded && (sonic == null || sonic.getFramesAvailable() == 0);
  }

  @Override
  public void flush() {
    if (isActive()) {
      if (sonic == null) {
        sonic = new Sonic(sampleRateHz, channelCount, speed, pitch, outputSampleRateHz);
      } else {
        sonic.flush();
      }
    }
    outputBuffer = EMPTY_BUFFER;
    inputBytes = 0;
    outputBytes = 0;
    inputEnded = false;
  }

  @Override
  public void reset() {
    speed = 1f;
    pitch = 1f;
    channelCount = Format.NO_VALUE;
    sampleRateHz = Format.NO_VALUE;
    outputSampleRateHz = Format.NO_VALUE;
    buffer = EMPTY_BUFFER;
    shortBuffer = buffer.asShortBuffer();
    outputBuffer = EMPTY_BUFFER;
    pendingOutputSampleRateHz = SAMPLE_RATE_NO_CHANGE;
    sonic = null;
    inputBytes = 0;
    outputBytes = 0;
    inputEnded = false;
  }

}
