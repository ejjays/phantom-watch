# Release

## What a release is

One APK for the family group. Debug and release install side by side
(`dev.phantomwatch.debug` and `dev.phantomwatch`), each with its own data.

## Checklist

```sh
python3 checks/quality.py
./gradlew :app:testDebugUnitTest --offline
node --test tests/*.test.mjs
node worker/test.mjs
./gradlew :app:assembleRelease --offline
```

Then:

1. Check the size and the version in `app/build/outputs/apk/release/output-metadata.json`.
2. Verify the signature matches the build that is already installed:
   `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`
3. Move the APK out of the build folder:
   `mv app/build/outputs/apk/release/app-release.apk /storage/emulated/0/release/`
4. Keep the R8 mapping next to it - release crash reports are obfuscated and
   cannot be read without it:
   `cp app/build/outputs/mapping/release/mapping.txt /storage/emulated/0/release/mapping-<versionCode>.txt`
5. Tell the family to install it (Android asks to update the existing app).
6. Watch the provider telemetry for the next hour:
   `curl -s "$TELEMETRY_URL_WITHOUT_E?k=$TELEMETRY_KEY&days=1"` -> the `/health` endpoint.

## Versioning

`versionCode` is the number of commits in the repository. Use a full clone -
a shallow clone or a source zip reports `1`, which is a downgrade from any
installed build. Keep history linear: squashing or rebasing can lower the
count. `versionName` stays `1.0`.

## Signing

The release is signed with the machine's debug keystore
(`~/.android/debug.keystore`) so sideloaded updates install over each other
without any extra setup. That keystore is not a secret: anyone can build an
APK with the same key. It is acceptable for a family group and nothing else.

If the app ever leaves the family circle, switch to a private keystore:

```sh
keytool -genkeypair -v -keystore ~/phantom-release.jks -alias phantom -keyalg RSA -keysize 4096 -validity 10000
```

Store it outside the repository (and back it up twice), put the values in
`local.properties` or in the CI secrets, and point `signingConfigs` at it.
Nobody can update the installed app without that file.

## OTA config

Provider hosts, keys and the extractor code ship in a signed bundle:

```sh
python3 sign_config.py --version N     # N must be above the deployed version
wrangler pages deploy build/pages-config --project-name phantomwatch-config --branch main --commit-dirty=true
```

Deploy `build/pages-config/providers.json` alone - never the repository root.
The app verifies the signature against `app/src/main/assets/ota_pub.pem`,
refuses an older version and keeps the last accepted bundle in prefs.

The signing key lives in the `OTA_KEY` Actions secret, so a new machine can
still sign: `gh workflow run sign-config.yml -f version=N`.

## Rollback

Keep the last good APK in `/storage/emulated/0/release/`. To roll back, hand
the family the older file and install it (a lower `versionCode` installs with
`adb install -r -d`, but by hand Android refuses a downgrade - uninstall first
if needed).

For the config, roll back by signing and deploying the previous
`providers.json` with a higher version number.
