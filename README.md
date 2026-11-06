<img src="docs/icon.png" width="96" align="right" alt="Cruscotto icon">

# Cruscotto

[![Android CI](https://github.com/TexAlphaWolf/cruscotto/actions/workflows/android.yml/badge.svg)](https://github.com/TexAlphaWolf/cruscotto/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

An offline Android app for a monthly budget and a car garage, with an encrypted database and layered unlock (PIN bound to the device, biometrics, TOTP).

*Cruscotto* is Italian for "dashboard". The UI is available in English, German and Italian, chosen in Settings or following the phone.

## Features

**Budget**
- Splits the net salary (or the income recorded in a month) into categories by percentage.
- When a category overspends, the difference is taken from *buffer* categories in a chosen order, and every category's effective share is recalculated (e.g. Leisure 10% → 5%). If the buffers run out, the shortfall is shown.

**Transactions**
- Income and expenses grouped by category, per month, year or all time.
- Monthly income/expense bar chart.

**Garage**
- Cars with number plate, make/model, fuel and odometer.
- Deadlines by date and/or mileage (insurance, road tax, inspection, service…), optionally recurring. Overdue items turn red, items within 30 days or 1,000 km turn yellow.
- Service history with date, mileage, cost and place/garage. "Done, renew" logs the work and moves a recurring deadline to its next cycle; the cost can go straight into the budget.

**Yearly clean-up**
- On 1 January, at the first unlock, transactions and/or service history from previous years are deleted (each can be switched off). Cars, deadlines and categories stay.
- On 1 December, and a last time on 20 December, a notification (generic text, no amounts) and an in-app banner remind you to save the year first.

**PDF report**
- Any year's transactions as an A4 PDF: income/expense/balance summary, totals by category with bars, then every transaction grouped by month with monthly subtotals. Built on the phone with Android's `PdfDocument`, in the app's language and fonts.
- From Transactions (Year view), Settings → Backup, or the December reminder.

**Look and feel**
- Light theme in pastel azure, green and orange gradients; dark theme in greys and black with orange accent stripes; or follow the system. Chosen in Settings.
- Titles in Fraunces, text and figures in Manrope (tabular digits). Both fonts are bundled, so nothing is downloaded.

**Privacy and security**
- No `INTERNET` permission, Android cloud backup and device transfer disabled.
- SQLCipher (AES-256) database; the key is never stored in clear.
- PIN wrapped with PBKDF2 **and** a non-extractable Android Keystore key, so PIN guessing only works on the phone, through the app's attempt limit.
- Recovery code, strong-biometric unlock, optional TOTP second factor, auto-lock, no screenshots (`FLAG_SECURE`).
- JSON backup export/import through the system file picker.

See [SECURITY.md](SECURITY.md) for the design and the threat model.

## Performance

- Screens query only the date range they show (a month, a year, the last 12 months), and long lists are lazy rows with stable keys, so only what is on screen is composed.
- SQLCipher's native library loads on first unlock, not at app start; the lock-screen countdown runs only during a wait.
- `profileinstaller` installs the Compose libraries' startup profiles even for sideloaded APKs.
- Release builds are minified and resource-shrunk, keep only the app's three languages, and ship only the `arm64-v8a` native code.

## Tech stack

| Component | Version |
|---|---|
| Kotlin (AGP built-in Kotlin) / Compose compiler | 2.4.10 |
| Android Gradle Plugin / Gradle | 9.4.0 / 9.8.2 |
| Jetpack Compose BOM (Material 3) | 2026.09.00 |
| Room (KSP 2.3.13) | 2.8.5 |
| SQLCipher for Android | 4.19.1 |
| androidx.biometric | 1.1.0 |
| ZXing core (OTP QR code) | 3.5.3 |
| androidx.profileinstaller | 1.4.1 |

`minSdk` 30 (Android 11), `targetSdk` 36, `compileSdk` 37. Versions are the latest stable releases that work together as of November 2026.

## Architecture

```
app/src/main/java/dev/tex/cruscotto/
├── security/   Crypto (PBKDF2, AES-GCM, TOTP, Base32), DeviceKey (Keystore layer),
│               KeyVault (wrapped keys, attempt limit), Biometric (Keystore + BiometricPrompt)
├── session/    Session: lock state, opens/closes the database, auto-lock
├── data/       Room entities, DAO, AppDatabase (SQLCipher), Repository (+ JSON backup)
├── domain/     Budget (buffer rebalancing), Garage (deadline status) — pure Kotlin
└── ui/         Jetpack Compose: lock screens, Budget, Transactions, Garage, Settings
```

The database only exists while the app is unlocked: `Session` opens it with the unwrapped key, and on lock it closes it and wipes the key from memory. Business logic in `domain/` and the crypto primitives in `security/Crypto.kt` have no Android dependencies and are covered by JVM unit tests.

## Build and run

Requirements: an Android Studio release that supports AGP 9.4, Android SDK Platform 37, JDK 17 (bundled with Android Studio).

1. Clone the repository and open it in Android Studio.
2. Let the Gradle sync finish.
3. Connect a phone with USB debugging on and press **Run**.

From the command line:

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

## Tests

`app/src/test` covers:
- TOTP against the RFC 6238 test vectors, and Base32 round-trips;
- wrapping and unwrapping the database key with a PIN (wrong PIN rejected);
- recovery code format;
- buffer rebalancing, shortfall and income-vs-salary in the budget engine;
- deadline status levels by date and mileage.

CI runs them on every push and pull request, and uploads the debug APK as an artifact.

## Releases

Pushing a tag such as `v1.0.0` runs the release workflow, which builds a signed APK and attaches it to a GitHub Release. It needs four repository secrets:

| Secret | Content |
|---|---|
| `KEYSTORE_BASE64` | the release keystore, Base64-encoded |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

Create a keystore once and keep it safe (losing it means future updates can't be installed over the old app):

```bash
keytool -genkeypair -v -keystore release.jks -alias cruscotto -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks    # paste the output into KEYSTORE_BASE64
```

## License

[MIT](LICENSE). The bundled fonts, [Fraunces](https://github.com/undercasetype/Fraunces) and [Manrope](https://github.com/googlefonts/manrope), are under the SIL Open Font License 1.1 (see `third_party/fonts`); the files in `app/src/main/res/font` are static, Latin-subset instances of the variable fonts.
