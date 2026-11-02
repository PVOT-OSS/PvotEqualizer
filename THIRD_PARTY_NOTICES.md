# Third-party notices

## Tabler Icons

The icons in `app/src/main/res/drawable/` (`ic_equalizer.xml`, `ic_presets.xml`, `ic_settings.xml`)
are converted from [Tabler Icons](https://tabler.io/icons), Copyright (c) 2020-2026 Paweł Kuna,
MIT License. The license text is in `LICENSES/MIT.txt`.

## Prior art

PvotEqualizer's audio design was studied against two GPL-3.0 projects, with thanks:

- [OpenEQ](https://github.com/Turbofan3360/OpenEQ) — the session-broadcast approach for attaching an
  effect to other apps' audio.
- [Equalizer314](https://github.com/bearinmindcat/Equalizer314) — the `DynamicsProcessing` band
  model, its per-session handling and the limits of the public pre-EQ API.

No code from either project is included yet. `app/src/main/java/org/prauga/equalizer/dsp/` is an
independent implementation. If code is ever ported from one of them, its copyright and license
header stays in the file and the credit moves here.
