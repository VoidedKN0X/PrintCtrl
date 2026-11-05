# PrintCtrl

## Dependency verification

Gradle checks every library and plugin download against the sha256 checksums in `gradle/verification-metadata.xml` (checksums only, no PGP). When a dependency, plugin or the Gradle version changes, start the "Dependency verification metadata" workflow (`gh api -X POST repos/VoidedKN0X/PrintCtrl/actions/workflows/verification-metadata.yml/dispatches -f ref=main`), take the file from the job log (between the BEGIN/END markers: `base64 -d | gunzip`), check the diff (only Maven Central, Google Maven and Gradle Plugin Portal artifacts the build uses) and commit it in the same push. Never turn verification off or make it lenient to get a build through.

## Releasing

- A release is published only when `versionName` in `app/build.gradle.kts` is bumped. Bump `versionCode` by 1 at the same time.
- Tags are `vX.Y.Z` and releases are named "PrintCtrl X.Y.Z". Never add a build number (`-build.N`, "(build N)").
- Use the same version for every change that ships together; README, licence and CI changes don't need a bump.

## Changelog

Every new version needs a detailed entry at the top of `CHANGELOG.md`, under a heading for that version (match the existing headings). CI refuses to release without one, and the entry becomes the release notes on GitHub, in Obtainium and in the CtrlF-Droid repo.

- Write for people using the app, not for developers: what's new, what changed, what was fixed.
- Cover every user-visible change in the version, one bullet each, with enough detail to understand it without opening the app.
- Group with `### New`, `### Changed`, `### Fixed` and `### Security` when there's more than a couple of items.

## Filament usage

Bambuddy already tracks filament use and syncs it to Spoolman. PrintCtrl must never deduct filament on its own during or after a print, or usage is counted twice. It only reads usage and makes deliberate, confirmed manual edits (measure, adjust, assign).
