# Threat model

Audience: anyone who sideloads the APK. No accounts, no payment, no personal
data beyond TMDB ids.

## What the app holds

| Secret | Where it lives | Extractable from the APK? |
|---|---|---|
| TMDB key | `local.properties` -> `BuildConfig` | yes |
| OpenSubtitles key | `local.properties` -> `BuildConfig` | yes |
| Supabase anon key | `local.properties` -> `BuildConfig` | yes (it is an anon key) |
| Groq keys | `local.properties` -> `BuildConfig` | yes |
| Deepgram key | `local.properties` -> `BuildConfig` | yes |
| Telemetry key | `local.properties` -> `BuildConfig` | yes |
| OTA signing key | held off-repo, Actions secret as backup | no |
| Release keystore | held off-repo | no |

Everything in the first block is public to anyone who has the APK. That is
accepted: there is no account, nothing to steal, and the keys only buy
quota. If a key starts costing money, move that feature behind the Worker
instead of trying to hide it in the app.

## What is protected

- **The OTA config** is signed with ECDSA P-256 and verified against a pinned
  public key. A tampered bundle is rejected and the last accepted one stays
  in prefs. The private key is kept off-repo, with the Actions secret as the
  backup; installed devices never receive it.
- **Telemetry** carries TMDB ids, per-leg counts and timings. No titles, no
  stream URLs, no account.
- **The app's own data** (watch positions, cached config) is not backed up
  (`allowBackup="false"`).
- **Updates** are fetched from `127.0.0.1` with a one-shot token, without
  redirects, inside a size cap. The APK must have the same package, a
  non-lower `versionCode`, and the same signer as the installed app; release
  builds also pin the release certificate in `UPDATE_CERT_SHA256`. A foreign
  APK signed with the old debug key is rejected.

## Known exposures, accepted on purpose

- **The update helper activity is exported.** Any local app can start it, but
  it only talks to `127.0.0.1` with the token from the intent and can only
  install an APK signed with the app's key and pinned certificate. The worst
  case is a spurious installer prompt.
- **Losing the release keystore** ends updates for installed release builds.
  I keep it off-repo and back it up offline, twice. The same applies to the
  OTA signing key.
- **Cleartext traffic.** Only `127.0.0.1` is allowed in the clear, for the update
  helper. An `http://` provider stream will not load until its host is added to
  `res/xml/network_security_config.xml`.
- **The WebView bridge.** The page is a local `https://phantom.local/` blob
  with network loads blocked; only the signed script runs in it. The bridge
  can issue HTTP requests, so it is only as trustworthy as the signed script.
- **Provider keys in `providers.json`.** They are the provider's own
  obfuscation values, published in the bundle by necessity.

## If the audience grows

1. Move TMDB, OpenSubtitles, Groq and the telemetry key behind the Worker and
   hand the app short-lived tokens.
2. Pin the certificate of the config endpoint, the Worker and Supabase.
3. Rate-limit the telemetry endpoint and drop the shared key.
