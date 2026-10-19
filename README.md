# NX809J SDR Lab v0.2
Android APK prototype for the REDMAGIC NX809J. This is a **synthetic OFDM educational lab**, not a functioning TV receiver or a standards-compliant ISDB-T full-seg demodulator.

Features: 13 x 432 virtual QPSK subcarriers; genuine 8192-point IFFT/FFT; 1/8 cyclic prefix; simulated AWGN; BER/EVM measurements; read-only Android hardware feature, visible /dev node and MediaCodec diagnostics; diagnostic sharing.

**No RF capture, TV tuning, actual broadcasting, full-seg decoding, TMCC, FEC, MPEG-TS, EPG, or protected-content decoding.** Without an RF acquisition circuit, a mobile application cannot directly receive terrestrial RF broadcasts.

## Download APK
Go to [Actions](https://github.com/sugano244/yt-dlp-android-custom/actions/workflows/build-apk.yml), open the latest successful run, and download the `NX809J-SDR-Lab-debug-APK` artifact. Extract `app-debug.apk` and install on Android. This is a debug-signed test build, not a production release.

The old yt-dlp source was removed from the current branch with the user's permission; it remains accessible in Git history.
