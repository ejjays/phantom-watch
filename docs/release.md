# Release

## What a release is

One signed APK. Debug and release install side by side
(`dev.phantomwatch.debug` and `dev.phantomwatch`), each with its own data.

## CI release

The .github/workflows/build-release.yml workflow is the release path. It runs on
`workflow_dispatch` from `main` and takes a `tag` plus a `publish` flag that
defaults to false. The tag must contain `git rev-list --count HEAD`, so a stale
tag fails before the build.

The workflow writes keys into `local.properties`, decodes the keystore from
`PW_RELEASE_KEYSTORE_B64`, builds with `-PcompileSdk=36 -PtargetSdk=36`, and
checks the APK certificate against `PW_RELEASE_CERT_SHA256` with apksigner
before uploading the artifact.

With `publish` set, the workflow builds release notes from the top entry in
`app/src/main/assets/whatsnew.json` and fails if that version matches the
previous tag. It then creates the GitHub release and uploads the APK.
Publishing fires the .github/workflows/notify.yml workflow, which sends the FCM
release topic message through `scripts/notify.mjs`.

## Checklist

```sh
python3 checks/quality.py
./gradlew :app:testDebugUnitTest
node --test tests/*.test.mjs
node backend/worker/test.mjs
./gradlew :app:assembleRelease
```

`assembleRelease` needs the signing variables listed under Signing below. CI
supplies them from Actions secrets.

Then:

1. Check the size and the version in `app/build/outputs/apk/release/output-metadata.json`.
2. Verify the signature matches the build that is already installed:
   `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`
3. Keep the R8 mapping next to the APK: release crash reports are obfuscated and
   cannot be read without it:
   `cp app/build/outputs/mapping/release/mapping.txt mapping-<versionCode>.txt`
4. Publish the APK (GitHub release, direct download, however the update is
   shipped) and tell installed users to update (Android asks over the
   existing install).
5. Watch the provider telemetry for the next hour:
   `curl -s "$TELEMETRY_URL/health?k=$TELEMETRY_KEY&days=1"`

## Versioning

`versionCode` is the number of commits in the repository. Use a full clone:
a shallow clone or a source zip reports `1`, which is a downgrade from any
installed build. Keep history linear: squashing or rebasing can lower the
count. `versionName` stays `1.0`.

## Signing

Release builds are signed with a private keystore kept outside the
repository. The build reads it from the environment:

```sh
./gradlew :app:assembleRelease
```

expects `PW_RELEASE_KEYSTORE`, `PW_RELEASE_STORE_PASSWORD`,
`PW_RELEASE_KEY_ALIAS`, `PW_RELEASE_KEY_PASSWORD` and
`PW_RELEASE_CERT_SHA256` to be set. `PW_RELEASE_CERT_SHA256` is compiled into
the APK as `UPDATE_CERT_SHA256`.
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
python3 scripts/sign_config.py --version N     # N must be above the deployed version
wrangler pages deploy build/pages-config --project-name phantomwatch-config --branch main --commit-dirty=true
```

Deploy `build/pages-config/providers.json` alone, never the repository root.
The app verifies the signature against `app/src/main/assets/ota_pub.pem`,
refuses an older version and keeps the last accepted bundle in prefs.

The signing key lives in the `OTA_KEY` Actions secret, so a new machine can
still sign: `gh workflow run sign-config.yml -f version=N`.

## Rollback

Keep the last good APK somewhere safe. To roll back, hand users the older
file and install it (a lower `versionCode` installs with
`adb install -r -d`, but by hand Android refuses a downgrade; uninstall first
if needed).

For the config, roll back by signing and deploying the previous
`providers.json` with a higher version number.
