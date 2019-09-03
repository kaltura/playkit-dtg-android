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
package com.kaltura.dtg.exoparser.source.hls.playlist;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.kaltura.dtg.exoparser.C;
import com.kaltura.dtg.exoparser.drm.DrmInitData;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;

/** Represents an HLS media playlist. */
public final class HlsMediaPlaylist extends HlsPlaylist {

  /** Media segment reference. */
  @SuppressWarnings("ComparableType")
  public static final class Segment implements Comparable<Long> {

    /**
     * The url of the segment.
     */
    public final String url;
    /**
     * The media initialization section for this segment, as defined by #EXT-X-MAP. May be null if
     * the media playlist does not define a media section for this segment. The same instance is
     * used for all segments that share an EXT-X-MAP tag.
     */
    @Nullable
    final Segment initializationSegment;
    /** The duration of the segment in microseconds, as defined by #EXTINF. */
    final long durationUs;
    /**
     * The number of #EXT-X-DISCONTINUITY tags in the playlist before the segment.
     */
    final int relativeDiscontinuitySequence;
    /**
     * The start time of the segment in microseconds, relative to the start of the playlist.
     */
    final long relativeStartTimeUs;
    /**
     * The encryption identity key uri as defined by #EXT-X-KEY, or null if the segment does not use
     * full segment encryption with identity key.
     */
    public final String fullSegmentEncryptionKeyUri;
    /**
     * The encryption initialization vector as defined by #EXT-X-KEY, or null if the segment is not
     * encrypted.
     */
    final String encryptionIV;
    /**
     * The segment's byte range offset, as defined by #EXT-X-BYTERANGE.
     */
    final long byterangeOffset;
    /**
     * The segment's byte range length, as defined by #EXT-X-BYTERANGE, or {@link C#LENGTH_UNSET} if
     * no byte range is specified.
     */
    final long byterangeLength;

    /** Whether the segment is tagged with #EXT-X-GAP. */
    final boolean hasGapTag;
    public final int lineNum;
    public final int encryptionKeyLineNum;

    /**
     * @param uri See {@link #url}.
     * @param byterangeOffset See {@link #byterangeOffset}.
     * @param byterangeLength See {@link #byterangeLength}.
     * @param lineNum
     * @param encryptionKeyLineNum
     */
    public Segment(String uri, long byterangeOffset, long byterangeLength, int lineNum, int encryptionKeyLineNum) {
      this(uri, null, 0, -1, C.TIME_UNSET, null, null, byterangeOffset, byterangeLength, false, lineNum, encryptionKeyLineNum);
    }

    /**
     * @param url See {@link #url}.
     * @param initializationSegment See {@link #initializationSegment}.
     * @param durationUs See {@link #durationUs}.
     * @param relativeDiscontinuitySequence See {@link #relativeDiscontinuitySequence}.
     * @param relativeStartTimeUs See {@link #relativeStartTimeUs}.
     * @param fullSegmentEncryptionKeyUri See {@link #fullSegmentEncryptionKeyUri}.
     * @param encryptionIV See {@link #encryptionIV}.
     * @param byterangeOffset See {@link #byterangeOffset}.
     * @param byterangeLength See {@link #byterangeLength}.
     * @param hasGapTag See {@link #hasGapTag}.
     */
    public Segment(
        String url,
        Segment initializationSegment,
        long durationUs,
        int relativeDiscontinuitySequence,
        long relativeStartTimeUs,
        String fullSegmentEncryptionKeyUri,
        String encryptionIV,
        long byterangeOffset,
        long byterangeLength,
        boolean hasGapTag,
        int lineNum,
        int encryptionKeyLineNum
    ) {
      this.url = url;
      this.initializationSegment = initializationSegment;
      this.durationUs = durationUs;
      this.relativeDiscontinuitySequence = relativeDiscontinuitySequence;
      this.relativeStartTimeUs = relativeStartTimeUs;
      this.fullSegmentEncryptionKeyUri = fullSegmentEncryptionKeyUri;
      this.encryptionIV = encryptionIV;
      this.byterangeOffset = byterangeOffset;
      this.byterangeLength = byterangeLength;
      this.hasGapTag = hasGapTag;
      this.lineNum = lineNum;
      this.encryptionKeyLineNum = encryptionKeyLineNum;
    }

    @Override
    public int compareTo(@NonNull Long relativeStartTimeUs) {
      return this.relativeStartTimeUs > relativeStartTimeUs
          ? 1 : (this.relativeStartTimeUs < relativeStartTimeUs ? -1 : 0);
    }

  }

  /**
   * Type of the playlist, as defined by #EXT-X-PLAYLIST-TYPE.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({PLAYLIST_TYPE_UNKNOWN, PLAYLIST_TYPE_VOD, PLAYLIST_TYPE_EVENT})
  public @interface PlaylistType {}
  public static final int PLAYLIST_TYPE_UNKNOWN = 0;
  public static final int PLAYLIST_TYPE_VOD = 1;
  public static final int PLAYLIST_TYPE_EVENT = 2;

  /**
   * The type of the playlist. See {@link PlaylistType}.
   */
  @PlaylistType
  private final int playlistType;
  /**
   * The start offset in microseconds, as defined by #EXT-X-START.
   */
  private final long startOffsetUs;
  /**
   * If {@link #hasProgramDateTime} is true, contains the datetime as microseconds since epoch.
   * Otherwise, contains the aggregated duration of removed segments up to this snapshot of the
   * playlist.
   */
  private final long startTimeUs;
  /**
   * Whether the playlist contains the #EXT-X-DISCONTINUITY-SEQUENCE tag.
   */
  private final boolean hasDiscontinuitySequence;
  /**
   * The discontinuity sequence number of the first media segment in the playlist, as defined by
   * #EXT-X-DISCONTINUITY-SEQUENCE.
   */
  private final int discontinuitySequence;
  /**
   * The media sequence number of the first media segment in the playlist, as defined by
   * #EXT-X-MEDIA-SEQUENCE.
   */
  private final long mediaSequence;
  /**
   * The compatibility version, as defined by #EXT-X-VERSION.
   */
  private final int version;
  /**
   * The target duration in microseconds, as defined by #EXT-X-TARGETDURATION.
   */
  private final long targetDurationUs;
  /**
   * Whether the playlist contains the #EXT-X-INDEPENDENT-SEGMENTS tag.
   */
  private final boolean hasIndependentSegmentsTag;
  /**
   * Whether the playlist contains the #EXT-X-ENDLIST tag.
   */
  private final boolean hasEndTag;
  /**
   * Whether the playlist contains a #EXT-X-PROGRAM-DATE-TIME tag.
   */
  private final boolean hasProgramDateTime;
  /**
   * DRM initialization data for sample decryption, or null if none of the segment uses sample
   * encryption.
   */
  private final DrmInitData drmInitData;
  /**
   * The list of segments in the playlist.
   */
  public final List<Segment> segments;
  /**
   * The total duration of the playlist in microseconds.
   */
  public final long durationUs;

  /**
   * @param playlistType See {@link #playlistType}.
   * @param baseUri See {@link #baseUri}.
   * @param tags See {@link #tags}.
   * @param startOffsetUs See {@link #startOffsetUs}.
   * @param startTimeUs See {@link #startTimeUs}.
   * @param hasDiscontinuitySequence See {@link #hasDiscontinuitySequence}.
   * @param discontinuitySequence See {@link #discontinuitySequence}.
   * @param mediaSequence See {@link #mediaSequence}.
   * @param version See {@link #version}.
   * @param targetDurationUs See {@link #targetDurationUs}.
   * @param hasIndependentSegmentsTag See {@link #hasIndependentSegmentsTag}.
   * @param hasEndTag See {@link #hasEndTag}.
   * @param hasProgramDateTime See {@link #hasProgramDateTime}.
   * @param drmInitData See {@link #drmInitData}.
   * @param segments See {@link #segments}.
   */
  public HlsMediaPlaylist(
      @PlaylistType int playlistType,
      String baseUri,
      List<String> tags,
      long startOffsetUs,
      long startTimeUs,
      boolean hasDiscontinuitySequence,
      int discontinuitySequence,
      long mediaSequence,
      int version,
      long targetDurationUs,
      boolean hasIndependentSegmentsTag,
      boolean hasEndTag,
      boolean hasProgramDateTime,
      DrmInitData drmInitData,
      List<Segment> segments) {
    super(baseUri, tags);
    this.playlistType = playlistType;
    this.startTimeUs = startTimeUs;
    this.hasDiscontinuitySequence = hasDiscontinuitySequence;
    this.discontinuitySequence = discontinuitySequence;
    this.mediaSequence = mediaSequence;
    this.version = version;
    this.targetDurationUs = targetDurationUs;
    this.hasIndependentSegmentsTag = hasIndependentSegmentsTag;
    this.hasEndTag = hasEndTag;
    this.hasProgramDateTime = hasProgramDateTime;
    this.drmInitData = drmInitData;
    this.segments = Collections.unmodifiableList(segments);
    if (!segments.isEmpty()) {
      Segment last = segments.get(segments.size() - 1);
      durationUs = last.relativeStartTimeUs + last.durationUs;
    } else {
      durationUs = 0;
    }
    this.startOffsetUs = startOffsetUs == C.TIME_UNSET ? C.TIME_UNSET
        : startOffsetUs >= 0 ? startOffsetUs : durationUs + startOffsetUs;
  }

  /**
   * Returns whether this playlist is newer than {@code other}.
   *
   * @param other The playlist to compare.
   * @return Whether this playlist is newer than {@code other}.
   */
  public boolean isNewerThan(HlsMediaPlaylist other) {
    if (other == null || mediaSequence > other.mediaSequence) {
      return true;
    }
    if (mediaSequence < other.mediaSequence) {
      return false;
    }
    // The media sequences are equal.
    int segmentCount = segments.size();
    int otherSegmentCount = other.segments.size();
    return segmentCount > otherSegmentCount
        || (segmentCount == otherSegmentCount && hasEndTag && !other.hasEndTag);
  }

  /**
   * Returns the result of adding the duration of the playlist to its start time.
   */
  public long getEndTimeUs() {
    return startTimeUs + durationUs;
  }

  /**
   * Returns a playlist identical to this one except for the start time, the discontinuity sequence
   * and {@code hasDiscontinuitySequence} values. The first two are set to the specified values,
   * {@code hasDiscontinuitySequence} is set to true.
   *
   * @param startTimeUs The start time for the returned playlist.
   * @param discontinuitySequence The discontinuity sequence for the returned playlist.
   * @return The playlist.
   */
  public HlsMediaPlaylist copyWith(long startTimeUs, int discontinuitySequence) {
    return new HlsMediaPlaylist(
        playlistType,
        baseUri,
        tags,
        startOffsetUs,
        startTimeUs,
        /* hasDiscontinuitySequence= */ true,
        discontinuitySequence,
        mediaSequence,
        version,
        targetDurationUs,
        hasIndependentSegmentsTag,
        hasEndTag,
        hasProgramDateTime,
        drmInitData,
        segments);
  }

  /**
   * Returns a playlist identical to this one except that an end tag is added. If an end tag is
   * already present then the playlist will return itself.
   *
   * @return The playlist.
   */
  public HlsMediaPlaylist copyWithEndTag() {
    if (this.hasEndTag) {
      return this;
    }
    return new HlsMediaPlaylist(
        playlistType,
        baseUri,
        tags,
        startOffsetUs,
        startTimeUs,
        hasDiscontinuitySequence,
        discontinuitySequence,
        mediaSequence,
        version,
        targetDurationUs,
        hasIndependentSegmentsTag,
        /* hasEndTag= */ true,
        hasProgramDateTime,
        drmInitData,
        segments);
  }

}
