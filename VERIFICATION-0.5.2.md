# 0.5.2 verification — 2026-09-15

- Ctrip splash advert reproduced, visible skip-ad countdown, no click count increase on 0.5.1.
- Runtime node evidence: skip TextView [847,141–1003,193] was not clickable; parent LinearLayouts [785,123–1031,211] and [763,123–1055,211] also not clickable. The next parent spans the entire screen width and must not be clicked.
- Added package-scoped gesture fallback at the freshly verified skip label center, with window/package/text/bounds/enable/exclusion checks and 2.5s cooldown. Toggle defaults on and can be disabled separately.
- Temporary runtime probe logging removed from source and final APK.
- Core 10122 checks and matching/bounds tests PASS; assembleDebug and lintDebug PASS.
- Final APK installed, versionCode 13 / versionName 0.5.2. Accessibility bound with capabilities=33 and no crashed services.
- Post-install first capture was locked (not a valid ad test). After unlocking, two observed launches displayed splash logo then home without an ad and with no click count increase. Therefore real Ctrip gesture dismissal is NOT yet verified.
- SHA256: 6AFCFAF6C2EF49C76BBA6E2D49CB3BBB387BEDDE7506CA5AC5B3682609AC8AE5
