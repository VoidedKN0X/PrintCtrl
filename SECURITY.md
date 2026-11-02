# Security policy

Thank you for helping keep PrintCtrl and its users safe.

## Reporting a problem

**Please don't report security problems in a public issue.** Report them privately instead:

1. Go to the [Security tab](https://github.com/VoidedKN0X/PrintCtrl/security) of this repo.
2. Click **Report a vulnerability** and describe what you found, how to reproduce it, and which version of PrintCtrl you used.

Only the maintainer can see the report. I'll reply as soon as I can, work on a fix, and credit you in the changelog if you'd like.

## Supported versions

Only the latest release gets security fixes. Please update before reporting, and check the [changelog](CHANGELOG.md) to see whether it's already fixed.

## What counts

- Your Bambuddy API key, proxy login, camera tokens or other data leaking to anyone other than your own Bambuddy or Spoolman server
- PrintCtrl trusting a certificate you didn't approve, or sending your key over plain HTTP to an address you didn't choose
- Ways around the app lock, or protected screens showing up in screenshots when they shouldn't
- Another app on the phone, a web page, a QR code, an NFC tag or a shared file being able to make PrintCtrl control a printer or upload something you didn't ask for
- Anything that lets someone run code or read files they shouldn't

Problems in Bambuddy or Spoolman themselves should go to those projects.

## How releases are protected

Every release is built from this repo by GitHub Actions and signed with the same release key. Its SHA-256 certificate fingerprint is:

```
7B:6A:AD:C9:11:DA:9F:1E:9E:12:6D:75:B7:AD:16:3B:D4:73:9E:9F:3E:56:E5:28:6D:29:15:13:9B:E1:06:1A
```

The [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) only accepts APKs signed with this key.
