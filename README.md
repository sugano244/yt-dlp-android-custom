# NX809J RF Verify v0.3

Android app for a **read-only feasibility diagnosis** of APK-only live over-the-air TV reception on REDMAGIC NX809J. Companion to synthetic OFDM SDR Lab.

## What the app actually checks
- Declared Android `android.hardware.tv.tuner` feature, and separate `android.software.live_tv` flag.
- Availability of Android TV tuner resource service to an ordinary application.
- Whether `android.permission.ACCESS_TV_TUNER` is granted. On stock Android 16 this is a signature/privileged/vendorPrivileged permission; merely asking cannot make it available to a normal APK.
- Accessible DVB/ISDB/SDR device nodes and limited VINTF manifest hints.
- Device model, SoC, Android version, supported MPEG-2/AVC video codecs.
- Still provides **synthetic** 13-segment-like OFDM FFT/IFFT BER tests for software performance.

The diagnostic cannot prove that undocumented RF hardware does not exist. Declared support is likewise not proof of actual ISDB-T reception. Even on a device with RF hardware, tuning and decoding protected broadcasts would need the applicable legitimate hardware and access rights.

**This APK receives no RF data and cannot show live television.** It makes no network requests, reads no personal identifiers, performs no rooting, and makes no changes to radios.

## APK
From [GitHub Actions](https://github.com/sugano244/yt-dlp-android-custom/actions/workflows/build-apk.yml), open the successful run and download `NX809J-SDR-Lab-debug-APK`. Install `app-debug.apk`.

Version 0.3 has a different Android application ID from v0.2, so it can be installed in parallel without debug-signing conflicts.

## References
- Android Open Source Project tuner framework: https://source.android.com/docs/devices/tv/tuner-framework
- Android hardware feature XML: https://android.googlesource.com/platform/frameworks/native/+/refs/heads/main/data/etc/android.hardware.tv.tuner.xml
- ACCESS_TV_TUNER permission (Android 16): https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-qpr2-release/core/res/AndroidManifest.xml
