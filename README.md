[![Codacy Badge](https://api.codacy.com/project/badge/Grade/8078d788646d4a62b52c83e57b3bea45)](https://www.codacy.com/app/PlayKit/playkit-dtg-android?utm_source=github.com&utm_medium=referral&utm_content=kaltura/playkit-dtg-android&utm_campaign=badger)
[![](https://jitpack.io/v/com.kaltura/playkit-dtg-android.svg)](https://jitpack.io/#com.kaltura/playkit-dtg-android) [![Travis](https://img.shields.io/travis/kaltura/playkit-dtg-android.svg)](https://travis-ci.org/kaltura/playkit-dtg-android)

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
	    compile 'com.kaltura:playkit-dtg-android:v2.0.0'
	}

Replace v2.0.0 with the latest release.

JitPack provides more options and information: https://jitpack.io/#com.kaltura/playkit-dtg-android


