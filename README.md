# Kryptex

An offline-first Android vault that hides encrypted notes, voice memos, pictures and files inside
ordinary PNG pictures. Optional quick unlock with a secret sequence of eye and face gestures,
recognised on-device.

Everything stays on the phone: the app has no network permission, no accounts, no cloud and no
analytics.

## Compatibility

| | |
|---|---|
| **Platform** | Android 10 (API 29) and newer |
| **Verified** | Samsung Galaxy devices |
| **Under active development** | Xiaomi (HyperOS): hardware / GPU compatibility and initialization patches are in progress |
| **Experimental** | All other Android devices |

### Known limitations

- **Xiaomi / HyperOS:** the app may fail to start or show a black screen on some Xiaomi devices.
  Compatibility fixes (start-up threading, a software-rendering fallback, native-library
  handling) are being developed and are not yet verified on hardware.
- **Other devices:** not tested. Gesture unlock in particular depends on the front camera and on
  MediaPipe running on the device; expect differences between manufacturers.
- The app blocks screenshots and screen recording on purpose (`FLAG_SECURE`), so a mirrored or
  recorded screen shows black even when the phone itself displays the app normally.

## Features

- **Hide in pictures:** payloads are encrypted (AES-256-GCM), then embedded in the pixels of a new
  PNG copy; the original picture is not changed.
- **Passphrase protection:** Argon2id key derivation, calibrated to the device at setup. There is
  no recovery: a forgotten passphrase means the contents are gone.
- **Encrypted database:** item metadata lives in Room on SQLCipher.
- **Gesture unlock (optional):** a secret sequence of blinks, winks, glances, eyebrow raises, mouth
  and smile gestures, recognised on-device with MediaPipe Face Landmarker. The camera starts only
  when you tap the button, and no image is stored. Five wrong attempts switch it off until the
  passphrase is used.
- **Memory hygiene:** keys and decrypted content are wiped when the vault locks, which happens as
  soon as the app leaves the foreground.
- **Languages:** English, Deutsch, Русский.

### Security notes

- A gesture sequence is a secret you *perform*, not a biometric check. Anyone who watches you can
  learn it, and it is weaker than a good passphrase. The passphrase always works.
- A picture that carries a hidden item loses it if it is edited, cropped, compressed or re-sent
  through a messenger. Keep the saved PNG exactly as it is.
- The merged manifest is checked at build time: the build fails if any permission other than
  `CAMERA` appears.

## Building

Requirements: JDK 17 or newer, Android SDK with platform 37, and an Android device or emulator
running Android 10+.

```
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # JVM unit tests
```

On Windows use `gradlew.bat`. Instrumented tests (`connectedDebugAndroidTest`) need a device or
emulator.

## Tech stack

Kotlin, Jetpack Compose (Material 3), Room with SQLCipher, Argon2id (argon2kt), CameraX, and
MediaPipe Tasks Vision (Face Landmarker, bundled model, verified by SHA-256 at build time).
