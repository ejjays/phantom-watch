# Phantom Watch

Android app for watching movies and shows. Search a title and it checks every
streaming source at once, keeps the best one, and starts playing. Subtitle and
audio choices are remembered, and each episode resumes where you stopped.

Free, with no ads. No account, no subscription, nothing to sign into. If a
source stops working, the fix arrives on its own the next time you open the app,
without downloading a new version.

## Install

Not on Google Play. Build the APK and sideload it:

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Android 8.0 or newer. The debug and release builds install side by side.

## Development

```sh
./gradlew :app:testDebugUnitTest --offline  # JVM tests
python3 checks/quality.py                   # repo invariants
node --test tests/*.test.mjs                # provider tests
node worker/test.mjs                        # worker tests
```

- [architecture](docs/architecture.md)
- [provider contract](docs/provider-contract.md)
- [release](docs/release.md)
- [threat model](docs/threat-model.md)

## Disclaimer

For personal use only. Stream only content you have the rights to.
