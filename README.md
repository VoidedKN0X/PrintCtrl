# PrintCtrl

**Your Bambu Lab printers and filament spools in one app.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/PrintCtrl?label=download)](https://github.com/VoidedKN0X/PrintCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

PrintCtrl is an Android companion for self-hosted [Bambuddy](https://github.com/maziggy/bambuddy) (Bambu Lab print management) and [Spoolman](https://github.com/Donkie/Spoolman) (filament inventory) servers. It talks only to your servers, never to the printers directly. The only other thing it can contact is VKNXWorks' own stats server, and only if you turn on [usage stats](#usage-stats).

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/PrintCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

## Features

- **Printers:** a card per printer with state, progress, layers, time left and the clock time it'll be done, live over Bambuddy's WebSocket, plus nozzle and bed temperature graphs for the last 15 minutes
- **Live camera** through Bambuddy's shared stream, with full screen, pinch to zoom and saving a picture to your gallery
- **Controls:** pause, resume, stop, print speed, light, fans, heater targets, home and jog, load and unload filament, skip parts, and "plate cleared" for Bambuddy's queue. Stop, resume, homing, loading and unloading filament, skipping parts, "plate cleared" and starting a queued job all ask first
- **AMS and AMS lite** slots with filament colour, material, what's left and the linked Spoolman spool
- **Printer messages (HMS)** with Bambu's own explanation, plus Obico AI failure detection
- **Maintenance, smart plug and SD card files** from Bambuddy on the printer page
- **Queue:** reorder, schedule, start and remove jobs; print library files with plate and AMS slot mapping; slice on the server; share a 3MF from another app to print it
- **Archive:** searchable print history with results, timelapses, "print again" and statistics, including filament used per material and what your prints cost per month, in the currency you choose
- **Spools:** your Spoolman inventory with search, filters, live weights, low-stock warnings, weigh-in and adjustments, SpoolmanDB lookup, QR label and NFC tag scanning, putting a spool in an AMS slot, and alerts when a spool runs low
- **Notifications without Google:** an ongoing progress notification while printing (a Live Update on Android 16), a camera picture after the first layer, and alerts by background checks or instantly through your own ntfy server (UnifiedPush)
- **Home-screen widget** and a **Quick Settings tile** with print progress
- **Bambuddy and Spoolman are each optional**: use the app with either one or both
- **Two addresses per server** (e.g. home and remote); you pick which one the app uses under **Account › Address in use**, and it stays on that one until you change it. PrintCtrl never switches addresses by itself
- **Self-signed certificates** trusted per server by fingerprint, never a blanket "trust all"
- **Private on the lock screen:** when your lock screen hides sensitive content, notifications show no printer or job names and no camera picture; Pause and Resume in a notification need the phone unlocked; and the recent-apps view shows a blank card for PrintCtrl
- **Shared files ask first:** a 3MF, G-code or STL shared to PrintCtrl is only uploaded to Bambuddy after you confirm

## Requirements

- Android 13 or newer
- A [Bambuddy](https://github.com/maziggy/bambuddy) server, a [Spoolman](https://github.com/Donkie/Spoolman) server, or both
- Optional: a camera for scanning QR codes, and NFC for spool tags
- No Google Play services needed: PrintCtrl is Google-free (no Firebase, Play services or Google servers; emoji use your phone's own font), so it works on de-Googled phones

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

1. In Bambuddy, open **Settings › API Keys** and create a key for PrintCtrl. Turn on the permissions for what you want to do from your phone:

   | Permission | What it allows in PrintCtrl |
   | --- | --- |
   | Read Status | See printers, camera, queue, library and archive (needed) |
   | Control Printer | Pause, resume, stop, temperatures, movement, filament load/unload, smart plug, SD card files |
   | Manage Queue | Add, reorder, schedule, start and remove jobs |
   | Manage Library | Upload files and slice on the server |
   | Manage Inventory | Put a Spoolman spool in an AMS slot |
   | Manage Maintenance | Mark maintenance as done |

2. Open PrintCtrl and pick **Bambuddy**. Tap **Scan the QR code from Bambuddy** and scan the code Bambuddy shows with the new key, or type the address (e.g. `bambuddy.lan:8000` or `https://bambuddy.example.com`) and paste the key.
3. Optionally add **Spoolman** too, now or later from the account page (tap the profile button at the top right).

## Notifications

Under **Account › Settings › Notifications**:

- **Progress notification:** on by default. It shows while a print runs and keeps the connection to Bambuddy open until the print ends. On Android 16 it's a **Live Update**: a progress chip in the status bar and a place at the top of the lock screen.
- **First-layer check:** on by default. Once the first layer of a print is down, a notification with a camera picture lets you check it started well, with Pause and Stop. It needs PrintCtrl to see the print start, so it works best with the progress notification on.
- **Alerts**, two ways, neither using Google services:
  - **Check every 15 minutes:** nothing to set up on the server; alerts can be up to 15 minutes late.
  - **Instantly, through ntfy:**
    1. Install [ntfy](https://f-droid.org/packages/io.heckel.ntfy/) and, if you self-host ntfy, set your server as its default server.
    2. In PrintCtrl choose **Instantly, through ntfy**, pick ntfy as the push app, and tap **Copy address**.
    3. In Bambuddy open **Settings › Notifications**, add a provider of type **Webhook**, paste the address as the webhook URL, keep the payload format **Generic**, and turn **attach photo** off (pictures are too big for a push message).
    4. Pick the events you want and send a test. It appears on your phone.
- **Background:** Android only lets the progress notification start with the app closed when PrintCtrl may run in the background. The notification settings show whether it can and open Android's prompt. On ColorOS (OPPO, OnePlus) also allow **auto launch** and **background activity** in the app's battery settings.

## Tips and troubleshooting

- **"Not allowed with this API key"** means the key is missing a permission. Edit the key in Bambuddy and turn on the one the action needs (see the table above).
- **Filament use** is counted by Bambuddy, which updates Spoolman after every print. PrintCtrl never takes filament off a spool by itself; weigh-ins and adjustments are only made when you confirm them.
- **Home and away:** add your LAN address first and your remote address second. PrintCtrl starts on the first address and never switches by itself, also not when your phone changes networks. Pick the address to use under **Account › Address in use**; when a server can't be reached, the Printers and Spools tabs show a button to use the other address.
- **Self-signed certificate:** PrintCtrl shows the certificate's fingerprint and asks before trusting it. Only that certificate is accepted for that address afterwards.
- **Plain HTTP** works, but the app warns you first because your API key would be sent unencrypted, and **Account › Servers** marks the server "Not encrypted (HTTP)".
- **Spoolman behind a login:** Spoolman has no login of its own; if your reverse proxy asks for one, enter it under the Spoolman server.
- **Camera:** the A1 and P1 cameras send about one picture a second. The picture comes from Bambuddy, which shares a single connection to the printer.

## Usage stats

PrintCtrl can share anonymous usage stats, **only if you say yes**. It asks once after you set up a server, and you can change it any time in **Account › Settings › Usage stats**.

- **Shared:** which screens you open (the kind of screen, never which printer, spool, file or print), whether Bambuddy, Spoolman or both are set up, your notification mode, which print controls you use (pause, resume, stop, and whether from the app or a notification), which kinds of spool changes you make (weigh, adjust, edit, link a tag, archive, add, put in or take out of a slot), the app version, Android version, phone model, language, and phone or tablet.
- **Never shared:** your server addresses, API keys or logins, printer names or serial numbers, file or job names, spool details (filament, colour, weight, location, price), or any ID that stays the same between sessions.
- **Where:** VKNXWorks' own self-hosted [Aptabase](https://aptabase.com) server. Nothing goes to Google or analytics companies. Like any website, the server sees your IP address; Aptabase uses it only to work out the country and doesn't store it.

Builds from source don't include the stats key, so they can't send anything and the setting is hidden. The code is in [`Telemetry.kt`](app/src/main/java/com/vknxworks/printctrl/data/telemetry/Telemetry.kt).

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
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [ElementCtrl](https://github.com/VoidedKN0X/ElementCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| **PrintCtrl** (this app) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |

## License

PrintCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

The Manrope font is licensed under the SIL Open Font License; see `FONT-LICENSE-Manrope.txt`.
