# PvotEqualizer

PvotEqualizer (`org.prauga.equalizer`) is the Pvot parametric equalizer: a draggable frequency curve
over a real audio engine, in Jetpack Compose on the Pvot design system. It is a PVOT-OSS app; the
design system lives in the `PvotLib/` git submodule, built as an included build
(`settings.gradle.kts`) and resolved as `com.prauga.pvot:design-system` and `core-ui`.

## Read first

The UI rules are PvotLib's, not this file's. Before changing UI read, in `PvotLib/`:
- `CLAUDE.md`: component table, tokens, working agreements.
- `docs/NEW_APP.md`: the playbook this app was started from, and `docs/GUIDE.md` for every token and
  motion spec.

Short version: use the Pvot component when one exists (`PvotNavBar`, `PvotCard`, `PvotScreen`,
`PvotSlider`, `PvotTextField`, `PvotDialog`, `PvotBottomSheet`, `PvotSectionHeader`, `UiState`
content). Colors only from `MaterialTheme.colorScheme`, space from `PvotSpacing`, shapes from
`PvotShapes`, motion from `PvotMotion`. No hex colors, no magic `dp`, all text in `strings.xml`.
Pvot has no buttons, switches, chips, list rows or segmented control yet: use Material 3 with default
parameters and list the gap rather than hand-styling it.

## What this app is

A parametric EQ for Android. The model is `EqProfile` = preamp plus a list of `ParametricBand`s
(type, frequency, Q, gain). Two consumers of that one model:

- the **curve on screen**, from the analytic biquad magnitude response;
- the **engine**, which fits the same curve onto `DynamicsProcessing` pre-EQ bands.

`android.media.audiofx.DynamicsProcessing` only takes bands with a cutoff frequency and a gain, and
AOSP renders them as a **staircase over FFT bins** with no interpolation, so the fit is an
approximation with a measured error budget — not a straight API call. Two hard limits to remember:
the public API clamps pre-EQ band count at **128** (asking for more throws), and coverage of other
apps is **best-effort** without root (session broadcasts from apps that opt in, plus an experimental
global attach).

## Working agreements

- **Never commit or push on your own.** Ask first. When told to commit, author and committer are
  `debz-g <77199373+debz-g@users.noreply.github.com>` (set `GIT_AUTHOR_*` and `GIT_COMMITTER_*`
  env vars, do not edit git config). Commit messages must not mention Claude, AI or an LLM, and
  carry no Co-Authored-By or "generated with" line. The same goes for PR descriptions.
- Pushes go to the fork `debz-g/PvotEqualizer`; `origin` is `PVOT-OSS/PvotEqualizer`. PRs target
  `origin` `main`.
- **Rebase, never merge.** A PR with a merge commit does not get merged. Keep history linear: bring
  in updates with `git fetch origin main && git rebase origin/main`, never `git merge`. Rebasing
  rewrites the branch, so pushing it needs `git push --force-with-lease` to the fork: ask first,
  once, and never to `origin`.
- Ask before downloads, deleting data, or anything hard to undo.
- Say what was verified and what was not. "It builds" is not "I looked at it on the phone".
- **Ask before using the test phone.** The S23 is a daily driver and other work may be running on it.

## Layout

```
app/src/main/java/org/prauga/equalizer/
  MainActivity.kt         PvotBaseActivity, theme
  ui/AppShell.kt          scaffold, PvotNavBar over a glass backdrop, three tabs
  ui/screens/             equalizer, presets, settings
  ui/components/          the curve and the band editor (app-local before PvotLib gets them)
  audio/                  AudioEngine, DynamicsProcessingEngine, EqService, session receiver, tile
  dsp/                    biquads, magnitude response, the DP band fit (pure Kotlin, unit tested)
  domain/                 ParametricBand, EqProfile, Preset, use cases
  data/                   EqPreferencesStore, PresetRepository, preset JSON
```

- `dsp/` must not import `android.*`. It is the part that can be tested on the JVM in seconds and it
  decides whether the sound matches the screen, so it is built and verified before the engine.
- The curve is drawn from the same numbers sent to the engine. Do not add a second source of truth
  for "what the EQ is doing".

## Toolchain

AGP 9.5.0, Kotlin 2.4.21, Java 21, Gradle 9.6.1, compile SDK 37.1, target SDK 36, min SDK 28
(`DynamicsProcessing`'s floor; PvotLib's is 27). CI is `PVOT-OSS/workflows` `android-build.yml` with
`java-version: '21'` and runs `assembleDebug assembleRelease lint`.

## Commands

```
export ANDROID_HOME=$HOME/Library/Android/sdk     # there is no local.properties
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease
./gradlew :app:testDebugUnitTest
./gradlew :app:installDebug                       # needs ANDROID_SERIAL if several devices
```

The `PvotLib` submodule is pinned to a commit on PvotLib `main`; update it deliberately with
`git -C PvotLib fetch && git -C PvotLib checkout <commit>`, never by tracking a branch.

## Testing on a device

- Test device is the user's Galaxy S23 over adb (`adb devices`). **Ask before installing or
  tapping: another test may be using the phone.**
- The phone is the user's daily phone. Look at the screen (`dumpsys window | grep mCurrentFocus`, a
  screenshot) before every tap, tap only inside this app, and never touch other apps.
- An EQ can only be verified by ear to a point: check the effect on a broadcasting player
  (Spotify/Poweramp), on Bluetooth, and after a reboot. Say what was heard and on which route.

## Licensing

GPL-3.0 (`LICENSE`). Prior art studied: [OpenEQ](https://github.com/Turbofan3360/OpenEQ) and
[Equalizer314](https://github.com/bearinmindcat/Equalizer314), both GPL-3.0. `dsp/` is our own
implementation; if any converter or DSP code is ported from either, keep its copyright and licence
header in the file and add the credit to `THIRD_PARTY_NOTICES.md`. Tabler icons (MIT) are credited
there too, with the per-file comment kept in each vector drawable.

## Open items

- Hilt/KSP is not wired up yet: the toolchain combination (AGP 9.5 with built-in Kotlin, KSP, Hilt)
  is unverified. Until it is, prefer plain constructors and a small manual container; decide before
  the screens grow ViewModels.
- Library gaps to propose to PvotLib: a vertical slider, a segmented control, a switch row, and
  eventually the EQ curve itself.
