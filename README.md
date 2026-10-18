# PrintCtrl

**Your Bambu Lab printers and filament spools in one app.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/PrintCtrl?label=download)](https://github.com/VoidedKN0X/PrintCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

PrintCtrl is an Android companion for self-hosted [Bambuddy](https://github.com/maziggy/bambuddy) (Bambu Lab print management) and [Spoolman](https://github.com/Donkie/Spoolman) (filament inventory) servers. It talks only to your servers, never to the printers directly.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/PrintCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

## Features

- **Printers tab** with a card per printer: state, progress, layers, time left and the clock time it'll be done
- **Live updates** over Bambuddy's WebSocket, with polling as a fallback
- **Live camera** through Bambuddy's shared stream, with full screen, pinch to zoom and saving a picture to your gallery
- **Controls**: pause, resume, stop (with a confirmation), print speed, light, fans and "plate cleared" for Bambuddy's queue
- **AMS and AMS lite** slots with filament colour, material and what's left
- **Printer messages (HMS)** with Bambu's own explanation and the code
- **Nozzle and bed temperature graphs** for the last 15 minutes
- **Spools tab** with your Spoolman inventory
- **Bambuddy and Spoolman are each optional**: use the app with either one or both
- **Two addresses per server** (e.g. home and remote); the app uses whichever answers
- **Self-signed certificates** trusted per server by fingerprint, never a blanket "trust all"

## Requirements

- Android 13 or newer
- A [Bambuddy](https://github.com/maziggy/bambuddy) server, a [Spoolman](https://github.com/Donkie/Spoolman) server, or both

## Install

1. Download the latest `.apk` file from the [Releases page](https://github.com/VoidedKN0X/PrintCtrl/releases/latest).
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings and servers are kept.

**Automatic updates:** on your phone, tap one of the buttons below.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds PrintCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/PrintCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/PrintCtrl` in Obtainium by hand.

## Getting started

1. In Bambuddy, open **Settings › API Keys** and create a key for PrintCtrl. Turn on **Read Status**, and **Control Printer** if you want to pause, resume and stop prints from your phone.
2. Open PrintCtrl and pick **Bambuddy**. Enter its address, e.g. `bambuddy.lan:8000` or `https://bambuddy.example.com`, and paste the key.
3. Optionally add **Spoolman** too, now or later from the account page (tap the profile button at the top right).

## Tips and troubleshooting

- **"Not allowed with this API key"** means the key is missing a permission. Edit the key in Bambuddy and turn on the one the action needs (Control Printer for printer controls).
- **Home and away:** add your LAN address first and your remote address second. PrintCtrl picks the first that answers and checks again when your phone switches networks.
- **Self-signed certificate:** PrintCtrl shows the certificate's fingerprint and asks before trusting it. Only that certificate is accepted for that address afterwards.
- **Plain HTTP** works, but the app warns you first because your API key would be sent unencrypted.
- **Spoolman behind a login:** Spoolman has no login of its own; if your reverse proxy asks for one, enter it under the Spoolman server.
- **Camera:** the A1 and P1 cameras send about one picture a second. The picture comes from Bambuddy, which shares a single connection to the printer.

## Build from source

You need [Android Studio](https://developer.android.com/studio), or JDK 17 and the Android SDK.

1. Clone the repo:
   ```bash
   git clone https://github.com/VoidedKN0X/PrintCtrl.git
   cd PrintCtrl
   ```
2. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows, use `gradlew.bat assembleDebug`.
3. The APK is in `app/build/outputs/apk/debug/`. Copy it to your phone and install it.

Or open the folder in Android Studio and press **Run** with your phone connected.

## Support the project

PrintCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). Starring the repo and [reporting bugs](https://github.com/VoidedKN0X/PrintCtrl/issues) helps too.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same Material You design.

| App | What it's for |
| --- | --- |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [ElementCtrl](https://github.com/VoidedKN0X/ElementCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| **PrintCtrl** (this app) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |

## License

PrintCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

The Manrope font is licensed under the SIL Open Font License; see `FONT-LICENSE-Manrope.txt`.
