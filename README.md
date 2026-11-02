# PvotEqualizer

A parametric equalizer for Android, on the [Pvot](https://github.com/PVOT-OSS) design system.

You shape a frequency curve directly: drag a point to move a band in frequency and gain, then set
its Q in the band editor. The same curve is what the audio engine applies, so what you see is what
you hear.

## Status

Early. The shell — three tabs on the Pvot nav bar — is in place. The DSP core (`dsp/`), the
`DynamicsProcessing` engine, the curve editor and presets are the next phases.

## Design notes

- **Parametric, not graphic.** Bands have a type, a frequency, a Q and a gain.
- **`dsp/` is pure Kotlin.** Biquad coefficients, magnitude response and the engine band fit have no
  `android.*` imports and are covered by JVM tests, because that maths decides whether the sound
  matches the screen.
- **Honest about coverage.** Android gives no rootless way to equalize every app. PvotEqualizer
  attaches to the audio sessions that apps hand to the standard effect-control broadcast, and offers
  an experimental all-apps mode. It does not sniff audio sessions through `dumpsys`.
- **No `INTERNET`, no analytics, no crash reporting, no microphone.** Presets stay on the device.

## Building

```
git clone --recurse-submodules https://github.com/PVOT-OSS/PvotEqualizer
cd PvotEqualizer
export ANDROID_HOME=$HOME/Library/Android/sdk
./gradlew :app:assembleDebug
```

JDK 21, Android SDK 37.1. The design system is the `PvotLib/` submodule, built as an included build.

## Licence

GPL-3.0. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
