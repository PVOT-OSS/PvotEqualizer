# PvotEqualizer

PvotEqualizer (`org.prauga.equalizer`) is the Pvot equalizer: a draggable frequency curve over a real
audio engine, in Jetpack Compose on the Pvot design system. It is a PVOT-OSS app; the design system
lives in the `PvotLib/` git submodule, built as an included build (`settings.gradle.kts`) and resolved
as `com.prauga.pvot:design-system` and `core-ui`.

The UI is a **fixed-band graphic EQ**: simple mode has 6 bands, advanced has 10, and the only thing a
user edits is gain. The model underneath is still parametric (`ParametricBand` with a type and Q), but
frequency, Q and type are not exposed, so the curve stays a curve and the drag stays honest.

## Read first

The UI rules are PvotLib's, not this file's. Before changing UI read, in `PvotLib/`:
- `CLAUDE.md`: component table, tokens, working agreements.
- `docs/NEW_APP.md`: the playbook this app was started from, and `docs/GUIDE.md` for every token and
  motion spec.

Short version: use the Pvot component when one exists (`PvotNavBar`, `PvotCard`, `PvotScreen`,
`PvotSlider`, `PvotTextField`, `PvotDialog`, `PvotBottomSheet`, `PvotSectionHeader`, `PvotSnackbar`,
`PvotCapsule`, `PvotSegmentedControl`, `PvotButton`, `UiState` content). Colors only from
`MaterialTheme.colorScheme`, space from `PvotSpacing`, shapes from `PvotShapes`, motion from
`PvotMotion`. No hex colors, no magic `dp`, all text in `strings.xml`.

When Pvot has no component for something, build it app-local, keep it small, and list it under "Open
items" rather than hand-styling several screens differently.

## What this app is

The model is `EqProfile` = preamp plus a list of `ParametricBand`s (type, frequency, Q, gain). Two
consumers of that one model:

- the **curve on screen**, from the analytic biquad magnitude response;
- the **engine**, which fits the same curve onto `DynamicsProcessing` pre-EQ bands.

`android.media.audiofx.DynamicsProcessing` only takes bands with a cutoff frequency and a gain, and
AOSP renders them as a **staircase over FFT bins** with no interpolation, so the fit is an
approximation with a measured error budget — not a straight API call. Two hard limits to remember:
the public API clamps pre-EQ band count at **128** (asking for more throws), and coverage of other
apps is **best-effort** without root: only apps that broadcast
`OPEN_AUDIO_EFFECT_CONTROL_SESSION` are affected. Say so in the UI rather than implying the EQ is
system-wide.

## Working agreements

- **Ask before pushing**, and never commit or push on your own initiative otherwise. When told to
  commit, author and committer are `debz-g <77199373+debz-g@users.noreply.github.com>` (set
  `GIT_AUTHOR_*` and `GIT_COMMITTER_*` env vars, do not edit git config). Commit messages must not
  mention Claude, AI or an LLM, and carry no Co-Authored-By or "generated with" line. The same goes
  for PR descriptions.
- `origin` is `PVOT-OSS/PvotEqualizer`, the real repository, and `main` tracks it. **There is no fork
  in the workflow.** The fork caused rebase and accidental merge-commit trouble; it is retired. (A
  `fork` remote may still be configured locally; do not push to it.)
- **`main` is protected**: it needs a review and the `android / build` check. Nothing lands by
  pushing to it, so commit on a branch even when a change feels small.
- **CI only runs for PRs whose base is `main`.** A stacked PR (base = another feature branch) gets no
  checks until it is retargeted, so prefer rebasing onto `main` once the parent PR merges.
- **New features and larger changes go on a branch**, pushed to `origin` and opened as a PR into
  `main`: `git switch -c feat/whatever`, then `git push -u origin feat/whatever`.
- **Rebase, never merge.** Keep history linear: bring a branch up to date with
  `git fetch origin main && git rebase origin/main`, never `git merge`. A rebased branch needs
  `git push --force-with-lease` after asking once. Never force-push `main`.
- Ask before downloads, deleting data, or anything hard to undo.
- Say what was verified and what was not. "It builds" is not "I looked at it on the phone".
- **Ask before using the test phone.** The S23 is a daily driver and other work may be running on it.

## Layout

```
app/src/main/java/org/prauga/equalizer/
  MainActivity.kt         PvotBaseActivity, theme, restores the power state on launch
  ui/AppShell.kt          scaffold, PvotNavBar over a glass backdrop, three tabs
  ui/screens/             equalizer, presets, settings
  ui/components/          EqCurve, EqSlider, ValuePill, UndoBar, ValueEntryDialog, BuiltInCurves
  audio/                  AudioEngine, DynamicsProcessingEngine, EqController, EqService
  dsp/                    Biquad, ProfileResponse, DpBandFit, ProfileEditing (pure Kotlin, tested)
  domain/                 EqProfile, ParametricBand, BandType, EqBands, EqMode, Preset
  data/                   EqStateStore, PresetJson, AutoEqImport
```

- `dsp/` must not import `android.*`. It is the part that can be tested on the JVM in seconds and it
  decides whether the sound matches the screen, so it is built and verified before the engine.
- The curve is drawn from the same numbers sent to the engine. Do not add a second source of truth
  for "what the EQ is doing".
- `EqBands` owns the band ladders and derives each band's Q from the spacing to its neighbours. That
  is what makes a drag on one band stay local instead of dragging the whole curve with it.

## State and file formats

- `EqStateStore` keeps everything in `SharedPreferences` named `eq_state`: the curve **per mode**
  (`profile_SIMPLE` / `profile_ADVANCED`), the power flag, and the saved presets. Writes are
  debounced; a malformed payload falls back to flat rather than crashing on launch.
- Both screens share one `EqualizerViewModel`, which is why applying a preset on one updates the
  other. The view model owns the curve and pushes it to `EqController`.
- `PresetJson` is the shared/stored form: versioned, tolerant of missing or unknown fields, keyed by
  frequency rather than band index so a preset survives a change of mode.
- `AutoEqImport` reads AutoEQ `ParametricEQ.txt` (preamp plus biquads) and `GraphicEQ.txt`
  (frequency/gain points), then fits that target onto our bands with correction passes. Import tries
  JSON first, then AutoEQ.
- Undo is **one level deep** and covers the destructive actions only: delete a preset, reset the
  curve, apply a preset, zero a band.

## Measured on the S23 (debug build)

Keep these in mind before changing `dsp/`:

- A fit is **~2.5 ms** warm and **~10.5 ms** on the first call in a fresh process (class loading and
  JIT). The cold number is what a user's first drag pays; warm the fit when the screen opens.
- Fitting accuracy, worst error against the analytic curve, 128 bands: **0.14 dB** for a musical
  six-band profile and **0.40 dB** at Q 10.
- A 512-point sampling grid is not enough: a Q 8 bell degrades to 1.32 dB. It is 1024 for a reason.
- Trigonometry is computed per frequency, not per band per frequency, and cutoff placement uses a
  priority queue rather than rescanning spans. Both were needed to get from 8.7 ms to 2.5 ms.

## Toolchain

AGP 9.5.0, Kotlin 2.4.21, Java 21, Gradle 9.6.1, compile SDK 37.1, target SDK 36, min SDK 28
(`DynamicsProcessing`'s floor; PvotLib's is 27). CI is `PVOT-OSS/workflows` `android-build.yml` with
`java-version: '21'` and runs `assembleDebug assembleRelease lint`.

## Commands

```
export ANDROID_HOME=$HOME/Library/Android/sdk     # there is no local.properties
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:installDebug                       # needs ANDROID_SERIAL if several devices
```

The `PvotLib` submodule is pinned to a commit on PvotLib `main` (currently `11b775e`); update it
deliberately with `git -C PvotLib fetch && git -C PvotLib checkout <commit>`, never by tracking a
branch. A bump is its own commit, and it builds against the app before it is committed.

## Release builds

`assembleRelease` runs R8 and resource shrinking, and produces an **unsigned** APK: there is no
signing config in the repository. Release APKs are aligned and signed locally with a keystore kept
**outside the repo**, at `../.local/pvot-release.jks` with its password beside it. Never commit a
keystore, a password file or `keystore.properties`. Losing that key means future builds cannot be
installed over this one.

## Testing on a device

- Test device is the user's Galaxy S23 over adb (`adb devices`). **Ask before installing or
  tapping: another test may be using the phone.** The wireless transport drops; pin
  `ANDROID_SERIAL` to the USB serial (`RZCX51G2MZA`) and re-check `adb devices` before a run.
- The phone is the user's daily phone. Look at the screen (`dumpsys window | grep mCurrentFocus`, a
  screenshot) before every tap, tap only inside this app, and never touch other apps. It locks
  itself: bring the app forward with `am start` rather than tapping blind.
- An EQ can only be verified by ear to a point: check the effect on a broadcasting player
  (Spotify/Poweramp), on Bluetooth, and after a reboot. Say what was heard and on which route.

## Licensing

GPL-3.0 (`LICENSE`). Prior art studied: [OpenEQ](https://github.com/Turbofan3360/OpenEQ) and
[Equalizer314](https://github.com/bearinmindcat/Equalizer314), both GPL-3.0. `dsp/` and
`AutoEqImport` are our own implementation; if any converter or DSP code is ported from elsewhere, keep
its copyright and licence header in the file and add the credit to `THIRD_PARTY_NOTICES.md`. Tabler
icons (MIT) are credited there too, with the per-file comment kept in each vector drawable.

## Open items

- **Migrate the app-local stand-ins to PvotLib.** `UndoBar` should become `PvotSnackbar`, `ValuePill`
  should become `PvotCapsule`, and the preset row could use `PvotSegmentedControl` or `PvotButton` now
  that those exist.
- **Boot restore and a Quick Settings tile** are not built. The power state is restored when the app
  is opened, not when the phone restarts.
- **Notification actions** offer Turn off only. Flat needs a change the open screen can observe, or it
  will show a stale curve.
- The **release APK** has never been launched under R8; it builds, but no one has run it.
- No instrumented tests. The curve's gestures are only covered by hand: a soak test would settle the
  one unreproduced report of a single tap moving several bands.
- Experimental global attach (session 0) is still unbuilt, so YouTube and Chrome are unaffected.
- Hilt/KSP is not wired up: the toolchain combination (AGP 9.5 with built-in Kotlin, KSP, Hilt) is
  unverified. Until it is, prefer plain constructors and the manual container in `di/AppContainer.kt`.
- Library gaps still worth proposing: a vertical slider, a switch row, and eventually the EQ curve
  itself.
