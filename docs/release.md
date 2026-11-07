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
set -a; . ~/.phantomwatch/release.env; set +a
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
   `curl -s "$TELEMETRY_URL/health?k=$TELEMETRY_KEY&days=1"`

## Versioning

`versionCode` is the number of commits in the repository. Use a full clone -
a shallow clone or a source zip reports `1`, which is a downgrade from any
installed build. Keep history linear: squashing or rebasing can lower the
count. `versionName` stays `1.0`.

## Signing

Release builds use the private keystore at `~/.phantomwatch/release.jks`
(alias `phantom`, RSA 4096). The build reads it from the `PW_RELEASE_*`
variables exported by `~/.phantomwatch/release.env`:

```sh
set -a; . ~/.phantomwatch/release.env; set +a
./gradlew :app:assembleRelease --offline
```

`PW_RELEASE_CERT_SHA256` is compiled into the APK as `UPDATE_CERT_SHA256`.
The update helper refuses an APK with a different package, an older
`versionCode`, a different signer, or (in release builds) a certificate that
does not match that fingerprint. Keep the keystore and the env file outside
the repository and back both up twice, offline: losing them means installed
apps can never be updated again.

A build signed with a new key cannot install over an app signed with the old
one. The first release after a key switch needs one uninstall on each device;
watch history is not backed up, so it is lost. The debug build is unaffected.

CI builds release with a throwaway keystore generated in the workflow
(`-PtestReleaseSigning=true`). That APK is a build check only: it is not
distributable and its pinned certificate is all zeros.

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
