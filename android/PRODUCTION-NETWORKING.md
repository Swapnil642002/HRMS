# Production networking preparation

This branch changes networking/configuration only. Production API base is
https://hrms.micromap.in/api/ and Origin is https://hrms.micromap.in. Staging uses
https://staging-hrms.micromap.in/api/ and its exact corresponding Origin. Staging
has a separate application ID and app data. Development uses emulator routing
through 10.0.2.2 while sending the backend's configured http://localhost:3000
Origin. Origin is a CSRF compatibility header, not an authentication credential.

Login displays a specific 429 message without automatic retries. Cookie matching
checks destination host/domain, path, HTTPS and expiry, including after restart.
Legacy cookie serialization is discarded, so existing users must sign in again.
Production/staging disable cleartext traffic and HTTP body logging. Business
rules, roles and attendance verification have not changed.

Validation performed with local Gradle 8.12, JDK 17 and Android SDK 36:

```bash
./gradlew --no-daemon :app:testStagingDebugUnitTest :app:assembleStagingDebug :app:compileProdReleaseKotlin
```

The wrapper is regenerated from Gradle 8.12 with the official distribution SHA-256
checksum. JAVA_HOME must point to JDK 17 (no machine-specific fallback remains).
The app now compiles/targets SDK 36 with AGP 8.10.1. Edge-to-edge uses Activity's
supported API, Scaffold safe-drawing insets are consumed before keyboard padding,
and logout clears the actual navigation graph. Predictive back uses Navigation
Compose and the platform dispatcher, with no deprecated onBackPressed override.

Before release, verify on an API 36 emulator/device using staging/test accounts:
- Gesture and three-button navigation, portrait/landscape and display cutouts.
- Login/recovery forms with keyboard open: inputs and submit buttons remain visible.
- Back from recovery returns to login; back from profile returns to its prior screen.
- Cancel a predictive back gesture: remain on the current screen.
- Complete back from the dashboard: leave the app, without reopening login underneath.
- Logout then Back never exposes the previous authenticated screen.
- Light/dark themes keep system-bar icons readable.

No device UI/gesture test or API call is implied by compilation and JVM tests.
Release signing and end-to-end API smoke testing remain outside this preparation.

Validation output, 2026-10-31 (regenerated wrapper):

```text
./gradlew --no-daemon :app:testStagingDebugUnitTest :app:assembleStagingDebug :app:compileProdReleaseKotlin :app:lintStagingDebug
BUILD SUCCESSFUL in 34s
68 actionable tasks: 16 executed, 52 up-to-date
NetworkSecurityTest: 4 tests, 0 failures, 0 errors
```

Lint passes with existing warnings; no baseline or suppression was added. Camera
hardware is explicitly optional to satisfy the SDK migration's manifest check.
