# Security design and threat model

Cruscotto stores personal finances and car records. Everything stays on the phone: the app has no `INTERNET` permission, and Android cloud backup and device-to-device transfer are disabled (`allowBackup="false"`, `data_extraction_rules.xml`).

## Keys

```
PIN ─────────▶ PBKDF2 ─▶ AES-GCM ─▶ Keystore AES-GCM (device-bound) ─┐
Recovery code ▶ PBKDF2 ─▶ AES-GCM ───────────────────────────────────┼─▶ dbKey ─▶ SQLCipher database
Biometrics ──────────────▶ Keystore AES-GCM (biometric auth) ────────┘
```

- **dbKey**: 256 random bits from `SecureRandom`. SQLCipher receives it as a raw key, so its own derivation is skipped. It is never written to disk in clear.
- **PIN path**: the PIN (4–12 digits) goes through PBKDF2-HMAC-SHA256 with 600,000 iterations and a random 16-byte salt. The result wraps dbKey with AES-256-GCM. That blob is then wrapped again with a non-extractable AES-256 key in the Android Keystore (StrongBox when the phone has one, otherwise the TEE).
- **Recovery code**: 16 Base32 characters (80 bits) shown once at setup. It wraps the same dbKey through its own PBKDF2 + AES-GCM. It is deliberately **not** device-bound, so it still works if the Keystore key is lost.
- **Biometrics**: a separate Keystore key with `setUserAuthenticationRequired(true)` and `AUTH_BIOMETRIC_STRONG`, used through `BiometricPrompt.CryptoObject`. It is invalidated when a new fingerprint or face is enrolled (`setInvalidatedByBiometricEnrollment(true)`); the app then falls back to the PIN.
- **TOTP** (optional): RFC 6238, SHA-1, 6 digits, 30-second steps, ±1 step of tolerance. The secret lives inside the encrypted database, so it can only be checked after the PIN or biometrics have opened it.

The wrapped keys, salts, attempt counter and auto-lock setting live in `noBackupFilesDir/vault.json`, written atomically. Theme, language and clean-up preferences are in ordinary app preferences: they hold no personal data.

## Unlock flow

1. PIN, or biometrics → dbKey → the database opens.
2. If TOTP is on, a valid code is required before any data is shown.
3. On lock (manual, or after leaving the app for the chosen time) the database is closed and dbKey is overwritten in memory.

The recovery code skips the OTP step and then asks for a new PIN: it is a much stronger secret (80 bits) than the PIN.

After 5 wrong attempts (PIN, recovery code or OTP), each further mistake imposes a wait that doubles from 30 seconds up to 1 hour.

## What this protects against

| Threat | Protection |
|---|---|
| Someone picks up the phone while the app is closed or locked | PIN / biometrics / TOTP; attempt limit with growing waits |
| Someone copies the app's files off the phone (backup tool, forensic image) and tries PINs on a computer | Not possible: the PIN blob is wrapped by a Keystore key that cannot leave the phone's secure hardware |
| Someone with the copied files tries recovery codes offline | 80-bit code behind 600k PBKDF2 iterations: out of reach |
| Data leaking through cloud backups or device transfer | Disabled in the manifest and extraction rules |
| Screenshots, screen recording, recent-apps preview | `FLAG_SECURE` |
| Data shown on the lock screen by notifications | The only notification (year-end reminder) has generic text with no amounts or names |
| Tampering with the encrypted data | AES-GCM authentication (key wrapping) and SQLCipher page HMACs |

## What it does not protect against

- **A rooted or compromised phone.** Malware with root can read the app's memory while it is unlocked, reset the attempt counter in `vault.json`, and run PIN guesses on the device through the Keystore. Each guess still costs a full PBKDF2 round (roughly a second on a recent phone), so a 4-digit PIN would fall in hours: **use 6–8 digits that are not a date or a number others know.**
- **The unlocked app.** While Cruscotto is open and unlocked, anyone holding the phone sees the data. The auto-lock limits that window.
- **Exported files.** The JSON backup and the PDF report are not encrypted. Keep them somewhere safe and delete copies you no longer need.
- **TOTP on the same phone.** If the authenticator app is on the same phone, the second factor adds little against someone who already holds that phone unlocked. It is most useful with the authenticator on another device.
- **PIN in memory.** The PIN is handled as a Kotlin `String`, which cannot be wiped reliably; it lives in memory only briefly during unlock.

## Losing access

The PIN and the recovery code are the only ways in. If both are lost, the data cannot be recovered by anyone, including the author. If the phone's Keystore key is lost (for example after certain system resets), the PIN stops working and the app asks for the recovery code, then for a new PIN.

## Reporting a vulnerability

Please open a [private security advisory](https://github.com/TexAlphaWolf/cruscotto/security/advisories/new) instead of a public issue.
