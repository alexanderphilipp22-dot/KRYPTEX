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
  when you tap the button, and no image is stored. A short vibration confirms each recognised
  gesture (optional, on by default). Five wrong attempts switch it off until the passphrase is used.
- **Face check (optional, on by default):** when gestures are set up, ten proportions of your face
  (ratios between the eye corners and points on the nose) are measured and saved. At unlock the
  face in front of the camera must fit them, so someone who has watched and copied your sequence
  is turned away. A face that does not fit looks exactly like a wrong sequence and uses up one of
  the five attempts. It can be switched off in Settings.
- **Memory hygiene:** keys and decrypted content are wiped when the vault locks, which happens as
  soon as the app leaves the foreground.
- **Languages:** English, Deutsch, Русский.

### Security notes

- A gesture sequence is a secret you *perform*. Anyone who watches you can learn it, and it is
  weaker than a good passphrase. The passphrase always works.
- **The face check is rough, and it is not a fingerprint-grade biometric.** Ten ratios from a
  landmark model tell clearly different faces apart, but look-alikes and relatives may pass, a
  photo or screen held to the camera is not detected (there is no liveness check), and poor light,
  a different distance or an unusual angle may turn the owner away. It is a gate in the app, not
  part of the key: the key still comes from the gesture sequence alone, so the check adds no
  cryptographic strength. Its thresholds are provisional and need tuning on real devices.
- **What is stored for the face check:** the ten numbers, never a picture. They are sealed together
  with the vault key inside the gesture key slot (Argon2id from the sequence plus the Android
  Keystore), so they can be read only with the right sequence on this phone, they are compared
  before the vault key is released, and they are wiped from memory afterwards. They never leave the
  device. Turning the check off in Settings stops the comparison but does not delete them; setting
  gestures up again replaces them, and removing gestures deletes them. Gestures set up with a
  version before the face check have none and are not checked until set up again. The only extra
  state outside the vault is one on/off flag in plain local preferences.
- A picture that carries a hidden item loses it if it is edited, cropped, compressed or re-sent
  through a messenger. Keep the saved PNG exactly as it is.
- The merged manifest is checked at build time: the build fails if any permission other than
  `CAMERA` (gesture unlock) and `VIBRATE` (the short pulse when a gesture is recognised, which can
  be switched off in Settings) appears. There is no network permission.

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
