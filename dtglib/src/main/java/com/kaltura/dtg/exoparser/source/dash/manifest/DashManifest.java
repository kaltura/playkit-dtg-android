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
package com.kaltura.dtg.exoparser.source.dash.manifest;

import android.net.Uri;

import com.kaltura.dtg.exoparser.C;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Represents a DASH media presentation description (mpd), as defined by ISO/IEC 23009-1:2014
 * Section 5.3.1.2.
 */
public class DashManifest {

  /**
   * The {@code availabilityStartTime} value in milliseconds since epoch, or {@link C#TIME_UNSET} if
   * not present.
   */
  private final long availabilityStartTimeMs;

  /**
   * The duration of the presentation in milliseconds, or {@link C#TIME_UNSET} if not applicable.
   */
  private final long durationMs;

  /**
   * The {@code minBufferTime} value in milliseconds, or {@link C#TIME_UNSET} if not present.
   */
  private final long minBufferTimeMs;

  /**
   * Whether the manifest has value "dynamic" for the {@code type} attribute.
   */
  private final boolean dynamic;

  /**
   * The {@code minimumUpdatePeriod} value in milliseconds, or {@link C#TIME_UNSET} if not
   * applicable.
   */
  private final long minUpdatePeriodMs;

  /**
   * The {@code timeShiftBufferDepth} value in milliseconds, or {@link C#TIME_UNSET} if not
   * present.
   */
  private final long timeShiftBufferDepthMs;

  /**
   * The {@code suggestedPresentationDelay} value in milliseconds, or {@link C#TIME_UNSET} if not
   * present.
   */
  private final long suggestedPresentationDelayMs;

  /**
   * The {@code publishTime} value in milliseconds since epoch, or {@link C#TIME_UNSET} if
   * not present.
   */
  private final long publishTimeMs;

  /**
   * The {@link UtcTimingElement}, or null if not present. Defined in DVB A168:7/2016, Section
   * 4.7.2.
   */
  private final UtcTimingElement utcTiming;

  /**
   * The location of this manifest.
   */
  private final Uri location;

  private final List<Period> periods;

  public DashManifest(long availabilityStartTimeMs, long durationMs, long minBufferTimeMs,
      boolean dynamic, long minUpdatePeriodMs, long timeShiftBufferDepthMs,
      long suggestedPresentationDelayMs, long publishTimeMs, UtcTimingElement utcTiming,
      Uri location, List<Period> periods) {
    this.availabilityStartTimeMs = availabilityStartTimeMs;
    this.durationMs = durationMs;
    this.minBufferTimeMs = minBufferTimeMs;
    this.dynamic = dynamic;
    this.minUpdatePeriodMs = minUpdatePeriodMs;
    this.timeShiftBufferDepthMs = timeShiftBufferDepthMs;
    this.suggestedPresentationDelayMs = suggestedPresentationDelayMs;
    this.publishTimeMs = publishTimeMs;
    this.utcTiming = utcTiming;
    this.location = location;
    this.periods = periods == null ? Collections.<Period>emptyList() : periods;
  }

  public final int getPeriodCount() {
    return periods.size();
  }

  public final Period getPeriod(int index) {
    return periods.get(index);
  }

  public final long getPeriodDurationMs(int index) {
    return index == periods.size() - 1
        ? (durationMs == C.TIME_UNSET ? C.TIME_UNSET : (durationMs - periods.get(index).startMs))
        : (periods.get(index + 1).startMs - periods.get(index).startMs);
  }

  public final long getPeriodDurationUs(int index) {
    return C.msToUs(getPeriodDurationMs(index));
  }

  private static ArrayList<AdaptationSet> copyAdaptationSets(
      List<AdaptationSet> adaptationSets, LinkedList<RepresentationKey> keys) {
    RepresentationKey key = keys.poll();
    int periodIndex = key.periodIndex;
    ArrayList<AdaptationSet> copyAdaptationSets = new ArrayList<>();
    do {
      int adaptationSetIndex = key.adaptationSetIndex;
      AdaptationSet adaptationSet = adaptationSets.get(adaptationSetIndex);

      List<Representation> representations = adaptationSet.representations;
      ArrayList<Representation> copyRepresentations = new ArrayList<>();
      do {
        Representation representation = representations.get(key.representationIndex);
        copyRepresentations.add(representation);
        key = keys.poll();
      } while(key.periodIndex == periodIndex && key.adaptationSetIndex == adaptationSetIndex);

      copyAdaptationSets.add(new AdaptationSet(adaptationSet.id, adaptationSet.type,
          copyRepresentations, adaptationSet.accessibilityDescriptors,
          adaptationSet.supplementalProperties));
    } while(key.periodIndex == periodIndex);
    // Add back the last key which doesn't belong to the period being processed
    keys.addFirst(key);
    return copyAdaptationSets;
  }

}
