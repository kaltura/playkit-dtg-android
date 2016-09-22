package com.kaltura.android.exoplayer.extractor.ogg;

import com.kaltura.android.exoplayer.extractor.Extractor;
import com.kaltura.android.exoplayer.extractor.ExtractorInput;
import com.kaltura.android.exoplayer.extractor.ExtractorOutput;
import com.kaltura.android.exoplayer.extractor.PositionHolder;
import com.kaltura.android.exoplayer.extractor.TrackOutput;
import com.kaltura.android.exoplayer.util.ParsableByteArray;

import java.io.IOException;

/**
 * StreamReader abstract class.
 */
/* package */ abstract class StreamReader {

  protected final ParsableByteArray scratch = new ParsableByteArray(
      new byte[OggParser.OGG_MAX_SEGMENT_SIZE * 255], 0);

  protected final OggParser oggParser = new OggParser();

  protected TrackOutput trackOutput;

  protected ExtractorOutput extractorOutput;

  void init(ExtractorOutput output, TrackOutput trackOutput) {
    this.extractorOutput = output;
    this.trackOutput = trackOutput;
  }

  /**
   * @see Extractor#seek()
   */
  void seek() {
    oggParser.reset();
    scratch.reset();
  }

  /**
   * @see Extractor#read(ExtractorInput, PositionHolder)
   */
  abstract int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException;
}
