# Build

## Prerequisites

- JDK 21 (Temurin is what CI uses)
- Android SDK with platform 37 and build-tools 35+
- Node 22, only for the provider and worker tests

Point `sdk.dir` in `local.properties` at your SDK. That file also carries keys
and is gitignored, so create it if it is not there:

```properties
sdk.dir=/path/to/android-sdk
```

The JVM heap is capped at 2 GB in `gradle.properties` because the app is
expected to build on modest hardware. Raise it if you have the RAM.

## Commands

```sh
./gradlew :app:assembleDebug          # debug APK in app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest      # JVM unit tests
./gradlew :app:detekt                 # lint
python3 checks/quality.py             # repo invariants (file caps, config schema, secrets)
node --test tests/*.test.mjs          # provider pick-order tests
node worker/test.mjs                  # telemetry worker tests
```

`compileSdk`/`targetSdk` are 37. If you do not have platform 37 installed,
build against 36 the way CI does:

```sh
./gradlew :app:assembleDebug -PcompileSdk=36 -PtargetSdk=36 -Pandroid.experimental.disableCompileSdkChecks=true
```

Releases are signed separately; see [release.md](release.md).

## Provider work

All provider logic lives in `app/src/main/assets/providers.js`. After touching
a provider leg, run it live against the current titles:

```sh
node probe.js
```

That needs network access and takes a couple of minutes; it appends to
`probe_history.jsonl`.
