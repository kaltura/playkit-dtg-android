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
package com.kaltura.android.exoplayer2.text.ttml;

import com.kaltura.android.exoplayer2.text.Cue;

/**
 * Represents a TTML Region.
 */
/* package */ final class TtmlRegion {

  private final String id;
  private final float position;
  private final float line;
  private final @Cue.LineType
  int lineType;
  private final @Cue.AnchorType int lineAnchor;
  private final float width;
  private final @Cue.TextSizeType int textSizeType;
  private final float textSize;

  public TtmlRegion(String id) {
    this(
        id,
        /* position= */ Cue.DIMEN_UNSET,
        /* line= */ Cue.DIMEN_UNSET,
        /* lineType= */ Cue.TYPE_UNSET,
        /* lineAnchor= */ Cue.TYPE_UNSET,
        /* width= */ Cue.DIMEN_UNSET,
        /* textSizeType= */ Cue.TYPE_UNSET,
        /* textSize= */ Cue.DIMEN_UNSET);
  }

  private TtmlRegion(
          String id,
          float position,
          float line,
          @Cue.LineType int lineType,
          @Cue.AnchorType int lineAnchor,
          float width,
          int textSizeType,
          float textSize) {
    this.id = id;
    this.position = position;
    this.line = line;
    this.lineType = lineType;
    this.lineAnchor = lineAnchor;
    this.width = width;
    this.textSizeType = textSizeType;
    this.textSize = textSize;
  }

}
