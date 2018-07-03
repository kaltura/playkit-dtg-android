/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.kaltura.android.exoplayer.hls;

import com.kaltura.android.exoplayer.chunk.Format;
import com.kaltura.android.exoplayer.chunk.FormatWrapper;

/**
 * Variant stream reference.
 */
public final class Variant implements FormatWrapper {

  public final String url;
  public final Format format;
  public final int firstLineNum;
  public final int lastLineNum;

  public Variant(String url, Format format, int firstLineNum, int lastLineNum) {
    this.url = url;
    this.format = format;
    this.firstLineNum = firstLineNum;
    this.lastLineNum = lastLineNum;
  }

  @Override
  public Format getFormat() {
    return format;
  }

}
