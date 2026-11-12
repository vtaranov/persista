# Changelog

## 0.9.0 — 2026-11-12

- Choose an alarm sound before saving a new rule. The draft keeps its app,
  phrase and sound when returning from the system sound picker; cancellation
  does not create a rule. Draft state is also saved for Activity recreation.
- Add a green **Snooze for 5 minutes** button above **Turn off** on the alarm
  screen. A snoozed reminder has a quiet notification with a cancel action.
- Schedule snoozes with a system alarm and play delivered repeats in a
  foreground media-playback service. The app holds neither the service nor
  its ringing wake lock during the five-minute wait. **Alarms and reminders**
  access is required for snooze.
- Keep overlapping reminders separate instead of replacing the active
  alarm's title while its old sound plays. Pausing alarms cancels pending
  snoozes. Old notification actions cannot stop a newer ringing session.
- Add version checks for notification, vibration and language APIs on
  Android 12–13, and nine JVM checks for snooze state and scheduling failures.

Validation: builds and the nine state checks passed; Android Lint reported
no errors. The corresponding debug build was tested on a Samsung Galaxy
S25 Ultra with Android 17: sound selection, draft cancellation, a five-minute
repeat with the screen off, vibration, lock-screen display, stopping, and a
local-calendar reminder. Existing rules and sounds were preserved.

The signed release APK was not separately installed on a device. Forced
Doze, process death, system Activity recreation, clock changes and extended
overlap scenarios were not separately tested on the device. Snooze is not
guaranteed after a reboot or force-stop.

## 0.8.0 — 2026-11-05

- Initial public release: app and phrase filters, per-rule alarm sounds,
  persistent ringing with vibration and a lock-screen alert, a Quick Settings
  pause tile, and English/Russian interfaces.
- Include the MIT license and notices for third-party components.
