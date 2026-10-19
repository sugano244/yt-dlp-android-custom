# NX809J Virtual Full-Seg Board v0.5

**Software-only research emulator of a fictional board** with a virtual RF tuner control-plane, Mode-3 OFDM cyclic-prefix acquisition stage, virtual memory-mapped chip registers, maskable/acknowledgeable IRQs, input-selection and tune state machine.

This deliberately **does not impersonate any commercial chip** and cannot drive a third-party proprietary kernel driver. The `RF_PRESENT` register is always `0`. No physical antenna, RF amplifiers/mixers, PLL or analog-to-digital circuit can be emulated into existence.

## Implemented (verified in CI)
- Virtual register addresses and 32-bit read/write semantics (`CHIP_ID`, control, status, channel/frequency, 8.126984 MS/s configuration, FFT size 8192, CP estimate, fractional CFO, spectral contrast, input source, IRQ/status).
- Power / reset / tune state machine and UHF channels 13–52. The specified frequency is informative only — the virtual tuner cannot retune the Snapdragon modem.
- Input source: NONE, SYNTHETIC (locally generated), RECORDED (Android document picker, interleaved CU8/CS16LE/CF32LE). Max 524,288 bytes.
- Real DSP execution of offline FFT + candidate cyclic-prefix synchronization from the v0.4 analyzer. Acquiring a CP candidate **never asserts** TMCC lock, valid transport stream, or real RF lock.
- IRQs: TUNE_DONE, CP_DETECTED, ERROR, IRQ mask and write-one-to-clear.
- Device diagnostics: Android tuner features, TV tuner permission, selected readable /dev and VINTF paths, video codec list.
- JVM CI tests for missing IQ, synthetic lock, noise false-positive guard, interrupts, read-only registers, and reset.
- App UI with virtual board actions, offline IQ import and shareable logs.

## NOT implemented or possible without hardware
- No direct over-the-air broadcast reception on standard NX809J: no discoverable compatible RF input.
- No emulated physical antenna/RF energy, mixer, ADC, or hardware PLL. The virtual component supplies only a *digital simulation*.
- No actual ISDB-T full-seg demodulation yet: no TMCC parsing, frequency/time interleaver reversal, channel estimation, FEC, MPEG-TS or B-CAS. A CP peak does NOT prove an ISDB-T signal.
- No audio/video playback. If a recorded real IQ file is loaded, the emulator inspects its CP and bandwidth signature, not content.

## Architecture
```
[Recorded IQ or synthetic generator] -> [Virtual tuner registers & digital IQ input]
  -> [OFDM Mode-3 CP search / CFO / FFT] -> [IRQ + status readouts]
  -> [TMCC/TS output: NOT IMPLEMENTED]
```

## Standards / upstream
- Japan ISDB-T base transmission: [ARIB STD-B31](https://www.arib.or.jp/kikaku/kikaku_hoso/desc/std-b31.html)
- Source of algorithm research (NOT included or copied): [gr-isdbt, GPL-3.0](https://github.com/git-artes/gr-isdbt)
- Physical radio feasibility was investigated on the user's NX809J via v0.3 diagnostics: no advertised TV tuner or usable TV tuner HAL.

## APK
[Build runs](https://github.com/sugano244/yt-dlp-android-custom/actions/workflows/build-apk.yml) -> latest green -> `NX809J-SDR-Lab-debug-APK` -> `app-debug.apk`.
