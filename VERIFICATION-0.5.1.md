# 0.5.1 verification — 2026-09-15

- Initial device inspection: accessibility enabled and bound, no crashed services; skip_ads=true. The user-reported ad has not been identified or reproduced.
- Fixed a code-level event-loss issue: eligible events received while throttled/busy are retained and scanned later. This is a potential contributor, not a proven explanation of the user's specific ad.
- On service connection explicitly restore all-package event subscription and node-reading flags. Cached package scope was not proven to be the cause.
- Added in-memory event/scan/result diagnostics without collecting screen text.
- Core/rule tests, assembleDebug, lintDebug: PASS. Android test APK compilation: PASS.
- Device fixture in separate test package: delayed button (burst), explicit close and marker-sibling close each reached CLICKED and removed the button; ordinary close without an ad marker remained WAITING. Three successful actions are reflected in the app's click count and are test actions, not real ads.
- Early apparent fixture failures were inconclusive: immediate uiautomator dump interfered with the running accessibility service. Final observations allowed four seconds before dumping.
- Final 0.5.1 APK installed successfully. Final screenshot shows filtering running and accessibility connected. No third-party ad dismissal or DNS self-test success claimed.
- APK SHA256: 08A9D60A0999CBE901BF80685DF7A544BC5458DF2BC1CFB87FD7BF63B2D2F96B
- Test activity is only in androidTest APK, absent from production APK.
