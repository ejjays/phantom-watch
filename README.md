<div align="center">
    <br/>
    <p>
        <img src="assets/logo.svg" title="phantom watch" alt="phantom watch" width="100" />
    </p>
    <p>
        <img src="assets/hero-title-light.svg#gh-light-mode-only" alt="Phantom Watch — Stop Paying. Start Watching" width="400" />
        <img src="assets/hero-title.svg#gh-dark-mode-only" alt="Phantom Watch — Stop Paying. Start Watching" width="400" />
    </p>
    <p>
        <a href="https://github.com/ejjays/phantom-watch/actions/workflows/build.yml">
            <img src="https://github.com/ejjays/phantom-watch/actions/workflows/build.yml/badge.svg" alt="build status" />
        </a>
        <a href="LICENSE"><img src="https://img.shields.io/badge/License-AGPLv3-blue?style=flat" alt="License: AGPL-3.0" /></a>
    </p>
    <p>
        <img src="assets/hero.svg" alt="Phantom Watch app screens" width="100%" />
    </p>
    <br/>
</div>

Phantom Watch is a free movie and TV app for Android built for people who
just want to watch. No subscription, no ads, nothing to set up.

### Get it

Grab the APK from [GitHub releases](https://github.com/ejjays/phantom-watch/releases)
or from [w-phantom.pages.dev](https://w-phantom.pages.dev).

### Building

```sh
./gradlew :app:assembleDebug
```

Prerequisites and the full test/lint commands are in [docs/build.md](docs/build.md).

Documentation lives in the [docs tree](/docs/):

- [Architecture](/docs/architecture.md)
- [Provider contract](/docs/provider-contract.md)
- [Build](/docs/build.md)
- [Release process](/docs/release.md)
- [Threat model](/docs/threat-model.md)

### Ethics

Phantom Watch is built to make accessing content easier. It takes zero
liability. You are responsible for what you watch and how you use the app.
It hosts nothing; a stream passes from its source to your screen and nothing
from it is kept.

### License

Phantom Watch is free software under [AGPL-3.0](LICENSE). Use it, study it,
change it, share it. If you run a modified version as a network service, the
AGPL requires you to offer its source to your users under the same license.
