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
package com.kaltura.android.exoplayer2.source.dash.manifest;

import com.kaltura.android.exoplayer2.metadata.emsg.EventMessage;

/**
 * A DASH in-MPD EventStream element, as defined by ISO/IEC 23009-1, 2nd edition, section 5.10.
 */
final class EventStream {

  /**
   * The scheme URI.
   */
  private final String schemeIdUri;

  /**
   * The value of the event stream. Use empty string if not defined in manifest.
   */
  private final String value;

  public EventStream(String schemeIdUri, String value, long[] presentationTimesUs,
                     EventMessage[] events) {
    this.schemeIdUri = schemeIdUri;
    this.value = value;
  }

  /**
   * A constructed id of this {@link EventStream}. Equal to {@code schemeIdUri + "/" + value}.
   */
  public String id() {
    return schemeIdUri + "/" + value;
  }

}
