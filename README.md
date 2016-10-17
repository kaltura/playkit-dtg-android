[![](https://jitpack.io/v/kaltura/playkit-dtg-android.svg)](https://jitpack.io/#kaltura/playkit-dtg-android)

# PlayKit DTG - Download To Go

Kaltura PlayKit DTG is an Android library that enables downloading MPEG-DASH and HLS streams for offline viewing.

* Track selection for video/audio/captions
* Widevine modular DRM

Documentation: https://vpaas.kaltura.com/documentation/Mobile-Video-Player-SDKs/Android-DTG.html

## Setup

The simplest way to get up and running is by using JitPack’s Maven repository.

1) Add JitPack’s repository to the top-level build.gradle:

	allprojects {
		repositories {
			...
			maven { url "https://jitpack.io" }
		}
	}

2) Add the dependency:

	dependencies {
	    compile 'com.github.kaltura:playkit-dtg-android:v2.0.0'
	}

Replace v2.0.0 with the latest release.

JitPack provides more options and information.

