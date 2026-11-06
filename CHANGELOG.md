# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-11-05

### Added
- Monthly budget: salary split into categories by percentage, with buffer categories that absorb overspending in a chosen order.
- Transactions view: income and expenses by category, per month, year or all time, with a monthly income/expense chart.
- Garage: cars with number plate and mileage, deadlines by date and/or mileage (optionally recurring), service history with date, mileage, cost and place.
- Service costs can be added to the budget in one step; "Done, renew" moves a recurring deadline to its next cycle.
- Security: SQLCipher-encrypted database, PIN bound to the Android Keystore, recovery code, strong-biometric unlock, optional TOTP second factor, attempt limit with growing waits, auto-lock, `FLAG_SECURE`.
- JSON backup export and import through the system file picker.
- English, German and Italian, selectable in Settings.
- Yearly clean-up of past years' transactions and service history, with reminders on 1 and 20 December.
- PDF report of a year's transactions (summary, totals by category, transactions by month).
- Performance: date-range queries, lazy lists, lazy native-library loading, startup profiles, smaller release APK.
- Light pastel theme (azure, green, orange gradients) and dark theme (greys and black with orange stripes), selectable in Settings; Fraunces and Manrope fonts bundled.
- App icon: a dashboard gauge in the theme's azure, green and orange on deep ink (adaptive, with a themed-icon version and a matching notification icon).
- CI workflow (unit tests and debug APK) and signed release workflow.
