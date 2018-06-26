package com.kaltura.android.exoplayer.util;

/**
 * Interface for manifests that are able to specify that subsequent loads should use a different
 * URI.
 */
public interface RedirectingManifest {

  /**
   * Returns the URI from which subsequent manifests should be requested, or null to continue
   * using the current URI.
   */
  public String getNextManifestUri();

}
