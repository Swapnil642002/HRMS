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

Validation performed with local Gradle 8.12, JDK 17 and Android SDK 35:

```bash
gradle --no-daemon :app:testStagingDebugUnitTest :app:assembleStagingDebug :app:compileProdReleaseKotlin
```

Four unit tests pass; staging APK builds and production Kotlin compiles. No app
was launched against staging or production. The repository's pre-existing Gradle
wrapper fails with missing org/gradle/wrapper/IDownload; use an installed Gradle
8.12 until the wrapper is separately repaired. Release signing and end-to-end
API smoke testing remain outside this preparation step.
