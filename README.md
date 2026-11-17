# PvotEqualizer

An equalizer for Android, on the [Pvot](https://github.com/PVOT-OSS) design system.

You shape a frequency curve directly: drag a band up or down, or fine-tune it in the band editor.
Six bands by default, ten in advanced mode. The same curve is what the audio engine applies, so
what you see is what you hear.

## Screenshots

<p align="center">
  <img src="docs/screenshots/equalizer.png" alt="Equalizer tab with the curve, preamp and band editor" width="220" height="469">
  &nbsp;
  <img src="docs/screenshots/curve.png" alt="Ten-band curve with clipping warning and band editor" width="220" height="469">
  &nbsp;
  <img src="docs/screenshots/preset-picker.png" alt="Preset picker sheet" width="220" height="469">
</p>
<p align="center">
  <img src="docs/screenshots/presets.png" alt="Presets tab with built-in and saved presets" width="220" height="469">
  &nbsp;
  <img src="docs/screenshots/import.png" alt="Import preset sheet: from a file or pasted JSON" width="220" height="469">
  &nbsp;
  <img src="docs/screenshots/settings.png" alt="Settings tab" width="220" height="469">
</p>

## Status

Early, but usable. What works today:

- **Curve editor** with a preamp, a clipping warning and one-tap auto preamp.
- **Simple and advanced modes**: 6 or 10 bands, each keeping its own curve.
- **Presets**: built-in curves (Flat, Bass, Vocal, Treble), saving your own, and importing from a
  file or pasted text. Import reads PvotEqualizer JSON as well as AutoEQ `ParametricEQ.txt` and
  `GraphicEQ.txt`.
- **One-level undo** for deleting a preset, resetting the curve, applying a preset and zeroing a band.
- **Audio engine** on `DynamicsProcessing` with a limiter against clipping, run from a foreground
  service whose notification offers Bypass/Resume and Turn off.
- **Quick Settings tile** to turn the equalizer on and off.
- **Test sound**: pink noise played through the curve, so you can hear it without another app.
- **After a restart**, a notification offers to turn the equalizer back on if it was on.
- **All apps (experimental)**: equalizes the whole output instead of only apps that share their
  audio session. Some devices ignore it.
- **Accessible**: every gain slider is labelled and read in dB; the curve can be read and its bands
  picked or reset with a screen reader.

## Design notes

- **A graphic EQ on a parametric model.** You only edit gain on fixed bands; underneath, each band
  is a biquad with a type, frequency and Q derived from its neighbours, so dragging one band only
  changes that part of the curve.
- **Honest fit.** `DynamicsProcessing` only takes cutoff/gain bands, so the curve is fitted onto up
  to 128 engine bands. Measured worst-case error is 0.14 dB for a typical six-band curve.
- **`:core:dsp` is pure Kotlin.** Biquad coefficients, magnitude response and the engine band fit live
  in a plain JVM module, so they cannot import `android.*`, and are covered by JVM tests, because that maths decides whether the sound
  matches the screen.
- **Honest about coverage.** Android gives no rootless way to equalize every app. PvotEqualizer
  attaches to the audio sessions that apps hand to the standard effect-control broadcast, so players
  like Spotify and Poweramp work, while YouTube and Chrome are not affected. It does not sniff audio
  sessions through `dumpsys`.
- **No `INTERNET`, no analytics, no crash reporting, no microphone.** Presets stay on the device.

## Building

```
git clone --recurse-submodules https://github.com/PVOT-OSS/PvotEqualizer
cd PvotEqualizer
export ANDROID_HOME=$HOME/Library/Android/sdk
./gradlew :app:assembleDebug
```

JDK 21, Android SDK 37.1. The design system is the `PvotLib/` submodule, built as an included build.

## Privacy

Equalizer collects nothing and has no internet permission. See [PrivacyPolicy.md](PrivacyPolicy.md).
The store listing lives in `fastlane/metadata/android/en-US/` and the F-Droid recipe in
`fdroid/metadata/`.

## Licence

GPL-3.0. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
