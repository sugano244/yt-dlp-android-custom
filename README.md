# NX809J IQ Field Lab v0.4

Android application to investigate offline ISDB-T signal processing on a REDMAGIC NX809J.

## Supported (implemented)
- Read-only NX809J RF feature/permission/hardware hints diagnostics (v0.3).
- A simulated educational 13-segment-like QPSK OFDM loopback (v0.2).
- Import locally recorded interleaved IQ with **Android system file picker** (no storage permission): CU8, CS16LE, CF32LE.
- FFT-size 8192 (ISDB-T Mode 3) and guard interval candidate search for 256, 512, 1024, 2048 samples.
- Calculate cyclic-prefix normalized autocorrelation, fractional CFO estimate and central/outer FFT energy contrast.
- Deterministic synthetic IQ capture to test algorithms, JVM tests of CP detection and rejection of noise, logs shareable from Android.
- Reads at most 524288 bytes; no recording, no network requests, no root.

**Correct sample rate required**: 8.126984 MS/s. If a capture uses another sample rate, it must be resampled before use. Center it on the ISDB-T channel. An apparent CP peak is *not* sufficient to identify a real ISDB-T signal.

## Not supported
- No live RF or actual TV; no native RF hardware channel; no tuning.
- No robust transmission-mode detection, fading-channel equalization, TMCC, interleaving, FEC, MPEG-TS, B-CAS, MPEG-2 decoding, or EPG.
- The offline analyzer is an early field-synchronization experiment, NOT a full-seg receiver.

## NX809J evidence
- The user-provided nonprivileged Android diagnostic: no advertised TV tuner, ACCESS_TV_TUNER denied, DVB device node absent, no TV HAL in readable top-level VINTF manifests.
- The [NX809J LineageOS device tree](https://github.com/IronShing/android_device_nubia_NX809J) (branch lineage-23) does not expose an ISDB-T/tuner HAL in selected manifest, vendor_hals.mk and proprietary-files.txt listings.
- Public [NX809J firmware inventory](https://github.com/code002-2/nubia-nx809j-firmware) lists modem, WLAN, Bluetooth, GPU and DSP firmware but no identified terrestrial broadcast frontend.
- These do NOT prove that undocumented hardware does not exist.

## Dependencies and compliance
- Reference implementation: [gr-isdbt](https://github.com/git-artes/gr-isdbt) GNU Radio full-seg receiver (GPL-3.0). We DO NOT ship or copy its code into this project. Porting the complete receiver would require a separate architecture, licensing review and substantial development.
- Typical ISDB-T Mode 3 nominal sampling ~8.126984 MS/s; FFT 8192.
- No copied proprietary Qualcomm or Nubia binary blobs.

## APK
[GitHub Actions builds](https://github.com/sugano244/yt-dlp-android-custom/actions/workflows/build-apk.yml) -> `NX809J-SDR-Lab-debug-APK` -> `app-debug.apk`.
