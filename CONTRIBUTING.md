# Contributing

Thanks for wanting to help. Read this and [docs/build.md](docs/build.md)
before your first change.

## How to work

- Open an issue before anything bigger than a one-line fix, so we agree on
  the approach before you spend an evening on it.
- Keep diffs small. One feature or one fix per PR.
- Run the checks before you push. CI runs the same ones and will bounce
  anything that fails locally:

  ```sh
  python3 checks/quality.py
  ./gradlew :app:testDebugUnitTest
  node --test tests/*.test.mjs
  ```

## Rules CI enforces

`checks/quality.py` is the source of truth. The short version:

- every `.kt` file's `package` line must match its directory under `src/*/java`
- one feature, one package (`core`, `data`, `provider`, `player`, `subs`,
  `social`, `torrent`, `ui`)
- file name matches the main type it holds; no grab-bag files
- no file over 900 lines
- no committed build output, no committed secrets

## Commit messages

`<area>: <3-6 words>`, lowercase, no trailing period. No body unless the WHY
is genuinely non-obvious. No `implement`/`refactor`/`improve`; a noun phrase
is usually better than "add". Examples from this repo:

```
player: fix landscape seek jump
deps: bump coil3 to 3.6.3
docs: build guide
```

## Never commit

`local.properties` (it carries keys), build output, and any editor or tool
dotfiles the repository does not use. The checks fail on committed build
output on purpose.

## Environment note

I maintain this repo on ARM64 Android devices (see the end of
[docs/build.md](docs/build.md)). Tooling suggestions that need x86 or a
desktop IDE are something I cannot test, so expect pushback there. It is a
hardware limit, not a judgement of the idea.
