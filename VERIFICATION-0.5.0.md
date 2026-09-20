# 0.5.0 verification — 2026-09-14

- Core tests: PASS, 10122 checks; subscription snapshot 105719 domains.
- Universal dismissal rules: PASS, explicit labels, ambiguous prompts, known system exclusions, Chrome/Mi browser eligibility, popup bounds and opening bounds.
- assembleDebug + lintDebug: PASS.
- Connected test device: final APK installed successfully; versionCode 11, versionName 0.5.0.
- Scope migration verified on device: general_scope_v1=true, all_apps=true; existing skip_ads=true retained.
- Device lifecycle instrumentation: FAILED at first DNS probe with SocketTimeoutException. No successful live DNS claim is made.
- Device was locked. Accessibility was enabled but unbound and listed as crashed after package replacement / instrumentation; no matching fatal exception found in inspected recent logs. Cause not established.
- Final APK installed again after browser eligibility correction. Starting VPN from adb is permission-protected; user must unlock to resume UI checks and restore filtering / accessibility.
- Actual third-party popup dismissal, browser behavior and battery consumption remain unverified.
- SHA256: 60ED36939B0D1C31A075153890D91CD60903D3C45212316EF7A91FB090CCF63D
