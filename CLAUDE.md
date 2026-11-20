# PrintCtrl

## Dependency verification

Gradle checks every library and plugin download against the sha256 checksums in `gradle/verification-metadata.xml` (checksums only, no PGP). When a dependency, plugin or the Gradle version changes, start the "Dependency verification metadata" workflow (`gh api -X POST repos/VoidedKN0X/PrintCtrl/actions/workflows/verification-metadata.yml/dispatches -f ref=main`), take the file from the job log (between the BEGIN/END markers: `base64 -d | gunzip`), check the diff (only Maven Central and Google Maven artifacts the build uses) and commit it in the same push. Never turn verification off or make it lenient to get a build through.

`settings.gradle.kts` lists only `google()` and `mavenCentral()` for plugins, not `gradlePluginPortal()`: the portal serves different files for the Kotlin plugin markers, which made verification fail whenever Maven Central had a hiccup. Don't add it back unfiltered (see the comment in that file).

## Build and signing (CI)

`.github/workflows/build.yml` has four jobs: `version` (is this a release?), `build` (Gradle, **no secrets**, `UNSIGNED_RELEASE=true` gives `app-release-unsigned.apk`; runs every check), `sign` (no checkout, no Gradle: `zipalign` + `apksigner` with the release key, then checks the signer against `EXPECTED_CERT_SHA256` and that the APK's contents didn't change) and `release` (attestation, release notes, publish).

- Never give the `build` job a secret, and never run Gradle or check out the repo in `sign`. New build checks (tests, lint, APK checks) go in `build`.
- `EXPECTED_CERT_SHA256` in `build.yml` must match the fingerprint in `SECURITY.md` and `README.md`; the build fails if it doesn't.
- No Gradle cache in any workflow (`cache-disabled: true`): a cache saved by a failed run used to break every later build.

## Releasing

- A release is published only when `versionName` in `app/build.gradle.kts` is bumped. Bump `versionCode` by 1 at the same time.
- Tags are `vX.Y.Z` and releases are named "PrintCtrl X.Y.Z". Never add a build number (`-build.N`, "(build N)").
- Use the same version for every change that ships together; README, licence and CI changes don't need a bump.

## Changelog

Every new version needs a detailed entry at the top of `CHANGELOG.md`, under a heading for that version (match the existing headings). CI refuses to release without one, and the entry becomes the release notes on GitHub, in Obtainium and in the CtrlF-Droid repo.

- Write for people using the app, not for developers: what's new, what changed, what was fixed.
- Cover every user-visible change in the version, one bullet each, with enough detail to understand it without opening the app.
- Group with `### New`, `### Changed`, `### Fixed` and `### Security` when there's more than a couple of items.

## Encryption at rest

- Three notification-state values in SharedPreferences are sealed with AES-256-GCM (`data/crypto/SealedText`: `"PRINTCTRL-REST1:"` + Base64 of a `SealedFile` blob) under the data key (`DataAtRest` / `DataKeyStore`: 32 random bytes in `filesDir/data.key`, wrapped by the Keystore key `printctrl_data_at_rest` through `KeystoreWrapper`/`KeystoreAes`; no user authentication, so the poll worker, the push receiver and the monitor service work while locked): `print_events` (`EventDetector`), `first_layer` (`FirstLayerCheck`) and `known_printers` (`KnownPrinters`).
- Readers must accept both sealed and ordinary values (`SealedText.open` returns ordinary values as they are). Without a data key (`DataAtRest.key` returns null) values are read and written the ordinary way. A sealed value that can't be opened is a cache miss ("never seen"), not an error.
- `DataAtRest.key` asks the Keystore the first time in a run: only call it off the main thread (the observers run on `appScope`/the worker).
- A sealed value differs every time it's made (random IV). Don't write one on every printer update: write only when the content changed or the stored copy isn't sealed yet (`EventDetector.write`), or SharedPreferences rewrites its file for every update.
- Never replace an existing `data.key`, and never create a Keystore key on the decrypt path (`KeystoreAes.existing`). The credential key (`KeystoreCrypto`, alias `printctrl_credentials`) and the app-lock vault are separate and must not be touched by this.
- Not sealed on purpose: the DataStore (`servers` with addresses, settings), `unifiedpush`, `drying_watch`, `low_stock`, `internal`, Coil's disk cache (cleared in `AppContainer.onLocked` instead) and `cacheDir/timelapse-*.mp4` (opened by Media3 and the share sheet).

## Filament usage

Bambuddy already tracks filament use and syncs it to Spoolman. PrintCtrl must never deduct filament on its own during or after a print, or usage is counted twice. It only reads usage and makes deliberate, confirmed manual edits (measure, adjust, assign).
