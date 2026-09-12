# QuietStart · 静启

[中文](README.md) | English

**QuietStart** (Chinese name: **静启**) is a no-root DNS ad filter for Android 13 and later. It uses Android's local VPN interface to block advertising domains. The app UI is currently in Chinese.

## Download

[Latest release](https://github.com/dskiiii/QuietStart/releases/latest) · [0.3.2 APK](https://github.com/dskiiii/QuietStart/releases/download/v0.3.2/QuietStart-0.3.2.apk)

Install the APK, tap “开启过滤” (Start filtering), approve the system VPN prompt, and wait for “过滤运行中” (Filtering). Tap “收起” (Hide) to close the page while filtering continues. The app is excluded from recent tasks; its launcher icon remains available.

The default scope includes Xiaohongshu, NetEase Cloud Music, and Baidu Maps. Pause filtering before changing the scope under Settings; all-app filtering is optional.

The release APK uses the existing development test signing key for compatibility with previous versions. Signing keys are not included. Locally rebuilt APKs usually have a different signature and cannot replace the release APK in place.

## Features

- Bundled anti-AD domain rules, with manual GitHub updates and fallback on update failure.
- Custom blocking, allowlist precedence, and a smaller rule mode.
- A home page with Start/Pause, Hide, and Settings.
- Request counters and optional local diagnostic recording.
- Blocking waits while idle; no WakeLock, periodic keepalive, or scheduled rule downloads. Battery use has not been measured.
- Optional [Clash Meta configuration merge utility](clash-integration/README.md) (Chinese instructions).

## Limitations

Android still displays its VPN indicator and service notification. QuietStart cannot share the same user's VPN slot with another VPN-based proxy. Background startup and battery settings can affect availability. System app-stop controls, VPN revocation, or another VPN taking over can stop filtering.

**Do not enable “Block connections without VPN.”** QuietStart routes only DNS, so that setting can prevent normal connectivity.

Domain filtering may block launch ads, popups, and banners, but does not guarantee complete ad removal. Cached ads, direct-IP requests, application-specific encrypted DNS, and ads sharing normal service endpoints may bypass filtering. QuietStart cannot dismiss another app's popup container, disable shake gestures, or inspect HTTPS content. Pause and adjust the allowlist if normal features are affected.

Client UDP DNS, including A/AAAA queries, is supported. Truncated upstream UDP replies are retried over TCP; client TCP DNS is not supported. Only the virtual DNS destination is routed into the app. Ordinary IPv4/IPv6 traffic is not forwarded by QuietStart.

## Privacy and diagnostics

No accounts, telemetry, automatic log upload, ad SDK, certificate installation, or accessibility permission. Settings and rules stay in private app storage with backup disabled.

Allowed DNS queries go to `223.5.5.5:53`, with `119.29.29.29:53` as fallback, using unencrypted DNS. Manual updates download public rules from GitHub without uploading diagnostic records.

Diagnostics use the same filtering logic as normal operation and only add logging. They do not restart the VPN, add rules, or clear other apps' caches. Recording is limited to 60 seconds and 600 events in process memory. Manually copied reports include domains and device details; review them before sharing. The self-test verifies only QuietStart's own DNS path, not another app's ad delivery.

## Build and validation

Requirements: JDK 17 and Android SDK Platform 35. The project uses Gradle 8.7 and Android Gradle Plugin 8.6.1. Set `JAVA_HOME` and create an untracked `local.properties` with `sdk.dir`, such as `sdk.dir=C:/Android/Sdk` on Windows.

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
# Java core checks only
powershell -ExecutionPolicy Bypass -File .\build.ps1 -TestOnly
```

Alternatively, run `./gradlew assembleDebug lintDebug`. The APK is written to `app/build/outputs/apk/debug/`.

Version 0.3.2 passed compilation, Android lint, APK signature verification, and 10,122 Java core checks, including 10,000 malformed-packet inputs. These are not device runtime tests. Pause/resume, notification behavior, ad effectiveness, and battery use still require device validation.

Build `assembleDebugAndroidTest`, install both APKs, and run the device-only idle-wait checks with:

```text
adb shell am instrument -w cn.quietstart.test/cn.quietstart.IdleWaitInstrumentation
```

These instrumentation tests have been compiled but not executed on a device.

## Version 0.3.2

Pause explicitly closes the VPN interface and filtering resources before updating the button, without waiting for service destruction. Previous reader threads and rule-loading callbacks are isolated from subsequent starts. Tapping the notification opens the app; pause is controlled inside the app.

## Third-party sources

Rules come from [anti-AD](https://github.com/privacy-protection-tools/anti-AD). The bundled snapshot is `20260908053823`, with 105,719 unique domains plus curated supplemental rules. Its [MIT license](app/src/main/assets/ANTI-AD-LICENSE.txt) is preserved.

The overall design was informed by AdAway, DNS66, and personalDNSfilter; their VPN engine source code was not copied. The rule data's license does not automatically license all project code.
