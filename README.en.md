0.6.0 adds HTTPS subscriptions and local rule-file import, with explicit confirmation for unsupported rules and domain exception support. Large imported lists now load without the observed startup timeout. A 16-launch device comparison did not establish that FilterFusion is better than anti-AD; both still showed splash ads. See [verification](VERIFICATION-0.6.0.md).

0.5.8 adds a strictly validated Baidu Maps splash-skip view-ID rule. 31 Android unit tests pass. No real splash ad appeared during this device retest; dismissal remains unverified. See [verification](VERIFICATION-0.5.8.md).

0.5.7 adds conservative countdown-skip matching with an adjacent visible ad marker. One NetEase device run logged the new click and then showed the home screen; a repeat without a captured ad is inconclusive. 26 Android unit tests pass. China Mobile remains unresolved. See [verification](VERIFICATION-0.5.7.md).

0.5.6: China Mobile countdown skip support requires a visible ad marker and revalidates the live button before touching its right-hand text. Two real early dismissals were observed with DNS off/on. This does not prevent ad loading. 21 desktop Android tests pass. See [verification](VERIFICATION-0.5.6.md).

0.5.5: abandon obsolete traversal after newer UI events and refresh Ctrip accessibility cache. Seventeen desktop Android tests pass. Two real Ctrip skips were observed with DNS paused during this session, alongside one failed attempt; the final build did not display another splash ad, so reliability remains unverified. See [device record](VERIFICATION-0.5.5.md).

0.5.4: query explicit ad-skip labels before the bounded traversal so late overlay nodes are not missed. Fifteen desktop Android regression tests pass; the OS gesture endpoint is mocked, and real Ctrip dismissal is still unverified. See [verification](VERIFICATION-0.5.4.md).

0.5.3 diagnostic build: Ctrip gesture cooldown now begins only after dispatch is accepted. Refreshed safe label bounds and the platform tap duration are used. The latest Ctrip gesture stage persists locally for diagnosis, without screen text. Real Ctrip ad dismissal remains unverified. See [verification](VERIFICATION-0.5.3.md).

> 0.5.2 adds an optional Ctrip-only gesture fallback for the exact, live top-right ad-skip label when normal node clicks are unavailable. Window, package, label and unchanged bounds are rechecked immediately before dispatch. No screenshots or fixed coordinates are used. The original issue was reproduced; after installation the splash ad did not reappear during retests, so successful real-ad dismissal remains unverified.

> 0.5.1: retain pending accessibility events during throttling/busy scans; refresh all-package service configuration on connection; show in-memory scan diagnostics. Device fixture checks passed for explicit ad close, delayed content, local ad-marker close, and an ordinary close negative case. Real third-party ads still need reproduction. UIAutomator dumps interfere with accessibility services and were taken only after the observation period.

> 0.5.0 universal filtering preview: first launch migrates DNS scope to all apps and browsers. Optional accessibility matching now covers eligible apps without a fixed app list, using explicit ad-dismiss labels or a visible ad marker beside a small close control. System UI is excluded; per-package exclusions and a popup sub-toggle are available. It does not implement browser cosmetic filtering. Click counts indicate issued actions, not verified dismissals. Earlier test notes below describe previous releases.

# QuietStart · 静启

[中文](README.md) | English

**QuietStart** (Chinese name: **静启**) is a no-root DNS ad filter for Android 13 and later. It uses Android's local VPN interface to block advertising domains. The app UI is currently in Chinese.

The current version is **0.6.0**. DNS filtering covers all apps and browsers by default. Optional accessibility rules attempt to dismiss explicitly identified ads, with additional splash compatibility for selected apps. Enable accessibility separately in Android settings. Pausing DNS filtering does not disable it. China Mobile remains unreliable, and real-ad dismissal with the new Baidu Maps rule is unverified. This is not browser cosmetic filtering and does not remove every ad.

Real-ad skipping remains unverified: an initial Ctrip cached ad was not skipped. After changing node access flags, subsequent launches did not display that ad, so no successful automatic click has been observed. The counter records click requests, not confirmed skips. This source also fixes duplicate-start status and adds startup timeout recovery; four pause/resume cycles, duplicate start, timeout recovery, and idle-wait checks passed on a Mi10 running Android 13. All-day battery use remains unmeasured.

## Download

[Latest release](https://github.com/dskiiii/QuietStart/releases/latest) · [0.6.0 APK](https://github.com/dskiiii/QuietStart/releases/download/v0.6.0/QuietStart-0.6.0.apk)

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

No accounts, telemetry, automatic log upload, ad SDK, or certificate installation. The optional 0.4.0 accessibility service reads interface nodes in the five listed apps and locally attempts explicit ad-skip clicks. It does not store or upload screen content, take screenshots, or tap fixed coordinates. Only a click-request count and the last clicked app package are stored. Settings and rules stay in private app storage with backup disabled.

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

The idle-wait instrumentation tests have passed on a Mi10 running Android 13. Add `-e lifecycle true` to run lifecycle checks after granting VPN permission; these checks pause and restart filtering. Label, package-scope, and button-boundary tests do not establish real-ad skipping effectiveness.

## Version 0.3.2

Pause explicitly closes the VPN interface and filtering resources before updating the button, without waiting for service destruction. Previous reader threads and rule-loading callbacks are isolated from subsequent starts. Tapping the notification opens the app; pause is controlled inside the app.

## Third-party sources

Rules come from [anti-AD](https://github.com/privacy-protection-tools/anti-AD). The bundled snapshot is `20260908053823`, with 105,719 unique domains plus curated supplemental rules. Its [MIT license](app/src/main/assets/ANTI-AD-LICENSE.txt) is preserved.

The overall design was informed by AdAway, DNS66, and personalDNSfilter; their VPN engine source code was not copied. The rule data's license does not automatically license all project code.
