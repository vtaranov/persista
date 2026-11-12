# Persista

Turn selected Android notifications into a persistent alarm with vibration
and a lock-screen alert. Filter by app and optional phrase, choose an alarm
sound while creating a rule, snooze a ringing alarm for five minutes, and
pause alarms from Quick Settings.

No ads, no internet access. English and Russian.

Download the APK from [Releases](https://github.com/vtaranov/persista/releases/latest), install it,
and grant the permissions requested by the app.

Android 12 or later is required. Enable notification access and app notifications;
allow full-screen alerts to show the alarm over the lock screen.
For five-minute snooze, also allow **Alarms and reminders** from the permission
card on the main screen. A snoozed alarm has a notification with a cancel action.
Pausing alarms cancels pending snoozes. Snoozes are not guaranteed after a device
reboot or an app force-stop.

GitHub release APKs use the same release signing key, so a new release can update
an earlier GitHub installation while preserving its rules. Debug builds use a
different key and cannot be updated in place with a release APK.

Build with JDK 17 and Android SDK 37:

```sh
./gradlew assembleDebug
./gradlew checkAlarmState lintDebug
```

`assembleRelease` produces an unsigned release APK. Distribution requires your
own signing key; the project does not include private keys or signing passwords.

Licensed under [MIT](LICENSE).
