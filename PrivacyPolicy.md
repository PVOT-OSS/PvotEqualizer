<!--
SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
SPDX-License-Identifier: GPL-3.0-only
-->

# Privacy Policy — Equalizer

_Effective 17 November 2026_

Equalizer (package `org.prauga.equalizer`) is an audio equalizer published by Saalim
Quadri for PVOT-OSS. This policy explains what the app does with your data.

**In short: the app doesn't collect, send, share or sell any personal data. It has no accounts, no
ads, no analytics, no crash reporting and no tracking, it does not use the microphone, and it has no
permission to use the internet.**

## What the app keeps on your device

- **Your curves**: the preamp and band gains for simple and advanced mode.
- **Your presets**: their names and curves.
- **Settings**: whether the equalizer is on, advanced mode, and the glass effect.

All of this stays in the app's private storage. Deleting a preset removes it straight away.
Everything is deleted when you clear the app's storage or uninstall it.

## Audio

Equalizer changes the sound of apps that hand their audio to equalizers through Android's standard
audio-effect broadcast. It applies an effect to that audio as it plays; it does not record, store or
analyse what you listen to.

While the equalizer is on, the app knows which of those apps opened an audio session (their package
name, for example `com.spotify.music`) so it can attach to and release them. This is held in memory
only, is written to Android's local system log for troubleshooting, and is never saved by the app or
sent anywhere.

## Backups

If you turn on Android's device backup, Android may include your curves, presets and settings in
your device backup or in a transfer to a new device (for example, Google's backup on most devices),
under the terms of that backup service. The app itself never sends them anywhere. You can turn
backup off in your device's settings.

## Sharing and importing

- When you share a preset, the app passes its name and curve as text to the app you pick in
  Android's share sheet. What happens to it there is up to that app.
- When you import a preset, the app reads only the file you pick in Android's file picker, or the
  text you paste.

Nothing is shared unless you choose to share it.

## Permissions

- `MODIFY_AUDIO_SETTINGS`: to apply the equalizer to audio.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: to keep the equalizer running while
  music plays in another app.
- `POST_NOTIFICATIONS`: for the ongoing notification Android requires while the equalizer runs. You
  can refuse it; the equalizer still works.
- `RECEIVE_BOOT_COMPLETED`: after a restart, if the equalizer was on, to post a notification that
  turns it back on when tapped. Nothing else runs at boot.

The app does not have the `INTERNET` or microphone (`RECORD_AUDIO`) permission.

## Children

The app is not directed at children and collects no data from anyone, children included.

## Changes

If the app starts handling data differently, this policy will be updated before that version is
released, with a new effective date at the top.

## Contact

Questions or concerns: open an issue at <https://github.com/PVOT-OSS/PvotEqualizer/issues> or email
Saalim Quadri at danascape@gmail.com.
