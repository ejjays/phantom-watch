# Threat model

Audience: family and a few friends, sideloaded APKs, no accounts, no payment,
no personal data beyond TMDB ids. This document says what is actually
protected and what is not, so nobody has to guess.

## What the app holds

| Secret | Where it lives | Extractable from the APK? |
|---|---|---|
| TMDB key | `local.properties` -> `BuildConfig` | yes |
| OpenSubtitles key | `local.properties` -> `BuildConfig` | yes |
| Supabase anon key | `local.properties` -> `BuildConfig` | yes (it is an anon key) |
| Groq keys | `local.properties` -> `BuildConfig` | yes |
| Seedbox password | `local.properties` -> `BuildConfig` | yes |
| Telemetry key | `local.properties` -> `BuildConfig` | yes |
| OTA signing key | `~/.phantomwatch/ota-key.pem` / Actions secret | no |
| Release keystore | `~/.android/debug.keystore` | it is a well-known key |

Everything in the first block is public to anyone who has the APK. That is
accepted: there is no account, nothing to steal, and the keys only buy
quota. If a key starts costing money, move that feature behind the Worker
instead of trying to hide it in the app.

## What is protected

- **The OTA config** is signed with ECDSA P-256 and verified against a pinned
  public key. A tampered bundle is rejected and the last accepted one stays
  in prefs. The private key lives on the original phone and in the Actions
  secret as the backup; the family devices never receive it.
- **Telemetry** carries TMDB ids, per-leg counts and timings. No titles, no
  stream URLs, no account.
- **The app's own data** (watch positions, cached config) is not backed up
  (`allowBackup="false"`).

## Known exposures, accepted on purpose

- **Debug-keystore release signing.** Anyone can sign an APK with the same
  key and install it over the app. For a family group that is fine; it stops
  being fine the moment the APK travels further. See
  [release.md](release.md) for the switch to a private keystore.
- **Cleartext traffic.** Some providers hand out `http://` streams, so
  `usesCleartextTraffic` is on. A hostile network could tamper with a stream.
  Nothing else travels in the clear.
- **The WebView bridge.** The page is a local `https://phantom.local/` blob
  with network loads blocked; only the signed script runs in it. The bridge
  can issue HTTP requests, so it is only as trustworthy as the signed script.
- **Provider keys in `providers.json`.** They are the provider's own
  obfuscation values, published in the bundle by necessity.

## If the audience grows

1. Private release keystore, kept offline, backed up twice.
2. Move TMDB, OpenSubtitles, Groq and the telemetry key behind the Worker and
   hand the app short-lived tokens.
3. Turn off cleartext and add a `network_security_config.xml` that only
   allows the hosts that need it.
4. Pin the certificate of the config endpoint, the Worker and Supabase.
5. Rate-limit the telemetry endpoint and drop the shared key.
