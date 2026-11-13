# PrintCtrl

**Your Bambu Lab printers and filament spools in one app.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/PrintCtrl?label=download)](https://github.com/VoidedKN0X/PrintCtrl/releases/latest) ![Android 15+](https://img.shields.io/badge/Android-15%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

PrintCtrl is an Android companion for self-hosted [Bambuddy](https://github.com/maziggy/bambuddy) (Bambu Lab print management) and [Spoolman](https://github.com/Donkie/Spoolman) (filament inventory) servers. It talks only to your servers, never to the printers directly. It has no telemetry of any kind ([Privacy](#privacy)).

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/PrintCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

## Features

- **Printers:** a card per printer with state, progress, layers, time left and the clock time it'll be done, live over Bambuddy's WebSocket, plus nozzle and bed temperature graphs for the last 15 minutes
- **Live camera** through Bambuddy's shared stream, with full screen, pinch to zoom and saving a picture to your gallery
- **Controls:** pause, resume, stop, print speed, light, fans, heater targets, home and jog, load and unload filament, skip parts, and "plate cleared" for Bambuddy's queue. Stop, resume, homing, loading and unloading filament, skipping parts, "plate cleared" and starting a queued job all ask first
- **AMS and AMS lite** slots with filament colour, material, what's left and the linked Spoolman spool
- **AMS drying** for an AMS 2 Pro or AMS HT: start with Bambuddy's preset per material (temperature, hours, spool rotation), see the stage, target and time left, stop it, and get a notification when it's done. It only shows for units that can dry, so not for an AMS or AMS lite (A1)
- **Printer messages (HMS)** with Bambu's own explanation, plus Obico AI failure detection
- **Maintenance, smart plug and SD card files** from Bambuddy on the printer page
- **Printer history and info:** temperature charts for the last hour up to a week (and AMS humidity where the AMS has a sensor), the printer's pressure advance (K-factor) profiles, and whether a firmware update is available
- **Queue:** reorder, schedule, start and remove jobs; print library files with plate and AMS slot mapping; slice on the server; share a 3MF from another app to print it
- **Library:** folders, renaming, moving and deleting files, and Bambuddy's trash to restore or remove them for good
- **Projects:** Bambuddy's projects with progress, prints, files, parts list, costs and timeline
- **Archive:** searchable print history with results, timelapses, "print again", favourites, notes and tags, deleting prints, and statistics, including filament used per material and what your prints cost per month, in the currency you choose
- **Spools:** your Spoolman inventory, or Bambuddy's own spool inventory if you don't use Spoolman, with search, filters, live weights, low-stock warnings, weigh-in and adjustments, archiving or deleting spools, deleting unused filaments and manufacturers, SpoolmanDB lookup, QR label and NFC tag scanning, putting a spool in an AMS slot, and alerts when a spool runs low
- **Notifications:** an ongoing progress notification while printing (a Live Update on Android 16), a camera picture after the first layer, and alerts by PrintCtrl's own background checks or instantly through **[ntfy](https://f-droid.org/packages/io.heckel.ntfy/)** and your own ntfy server, with the background checks as a fallback
- **Home-screen widget** and a **Quick Settings tile** with print progress
- **Bambuddy and Spoolman are each optional**: use the app with either one or both
- **Phones, tablets and foldables:** layouts that use the extra room in landscape and on big screens
- **Back up and restore** your settings and servers to a file (API keys and passwords are never included, and a restore never trusts a certificate from the file or allows plain HTTP to a new address without the usual warning)
- **Two addresses per server** (e.g. home and remote); you pick which one the app uses on the **You** page (tap your profile picture) under **Address in use**, and it stays on that one until you change it. PrintCtrl never switches addresses by itself
- **Self-signed certificates** trusted per server by fingerprint, never a blanket "trust all"
- **Private on the lock screen:** when your lock screen hides sensitive content, notifications show no printer or job names and no camera picture; Pause and Resume in a notification need the phone unlocked; and optional **Hide in screenshots** and **Hide in recent apps** switches (off by default)
- **App lock** with its own PIN and optional fingerprint unlock; your API key and logins stay encrypted while the app is locked
- **Shared files ask first:** a 3MF, G-code or STL shared to PrintCtrl is only uploaded to Bambuddy after you confirm

## Requirements

- Android 15 or newer, on a 64-bit ARM phone (nearly every phone from the last several years)
- A [Bambuddy](https://github.com/maziggy/bambuddy) server, a [Spoolman](https://github.com/Donkie/Spoolman) server, or both
- Optional: a camera for scanning QR codes, and NFC for spool tags
- **Google-free and de-Googled:** no Google Play services, Firebase or other Google services, and the app never contacts Google. It works the same on de-Googled phones (GrapheneOS, CalyxOS, /e/OS, LineageOS without Google apps).
- Optional: the [ntfy](https://f-droid.org/packages/io.heckel.ntfy/) app, for instant alerts

## Install

1. Open the [Releases page](https://github.com/VoidedKN0X/PrintCtrl/releases/latest) on your phone, scroll down to **Assets** and tap the file that ends in `.apk` (for example `PrintCtrl-v1.5.4.apk`). The "Source code" zip and tar.gz files are not the app.
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings and servers are kept.

**Automatic updates:** on your phone, tap one of the buttons below.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds PrintCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/PrintCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/PrintCtrl` in Obtainium by hand.

## Is it safe to install?

- **Open source:** all the code is in this repo, under the GPL-3.0 licence.
- **Built by GitHub, not on a personal computer:** every release on the Releases page is built from this code by [the build workflow](.github/workflows/build.yml) on GitHub Actions.
- **Always signed with the same key:** Android refuses an update that is signed with a different key, and the CtrlF-Droid repo only accepts APKs signed with this one. Its SHA-256 certificate fingerprint is:

  ```
  7B:6A:AD:C9:11:DA:9F:1E:9E:12:6D:75:B7:AD:16:3B:D4:73:9E:9F:3E:56:E5:28:6D:29:15:13:9B:E1:06:1A
  ```

  Advanced users can compare it with [AppVerifier](https://github.com/soupslurpr/AppVerifier).
- **Check a download:** the release notes list the APK's SHA-256 checksum, so you can compare it with the file you downloaded. With the [GitHub CLI](https://cli.github.com/), `gh attestation verify <apk> -R VoidedKN0X/PrintCtrl` proves the APK was built by this repo's build workflow from the tagged commit. The signing-certificate fingerprint above stays the check that matters for updates.

**Permissions** and what PrintCtrl uses them for:

| Permission | What it's for |
| --- | --- |
| Internet | Talking to your own Bambuddy and Spoolman servers, nothing else |
| Network state | Background checks only run when the phone is online |
| Use biometrics | Optional fingerprint unlock for the app lock |
| Hide other apps' floating windows | Hiding other apps' floating windows (chat heads, overlays) over the PIN screen, sign-in and "can't be undone" confirmations, so nothing can cover them. No prompt, and nothing changes on other screens |
| Notifications | Print progress, alerts, the first-layer check and low-stock warnings |
| Promoted notifications | The print progress as a Live Update chip in the status bar on Android 16 |
| Foreground service | Keeping the progress notification connected to Bambuddy while a print runs |
| Foreground service (special use) | The type of foreground service Android requires for that live print progress |
| Ignore battery optimisations | Only to open Android's own "allow background activity" prompt from the notification settings |
| Camera | Scanning Spoolman labels and Bambuddy's API key QR code |
| NFC | Reading spool tags |
| Prevent phone from sleeping | Keeping the phone awake for the few seconds a background check or drying reminder runs |
| Run at startup | Setting the background checks and a running drying reminder up again after the phone restarts |

The last two come with Android's background-work library, which runs the background checks. Android also lists a permission named after PrintCtrl itself (`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`): it only lets PrintCtrl's own parts talk to each other, and no other app can hold it. PrintCtrl has no permission for your contacts, location, microphone or files: 3MF, G-code and STL files reach it only when you share or open one with PrintCtrl.

What the app does and doesn't send is in [Privacy](#privacy).

## Getting started

1. In Bambuddy, open **Settings › API Keys** and create a key for PrintCtrl. Turn on the permissions for what you want to do from your phone:

   | Permission | What it allows in PrintCtrl |
   | --- | --- |
   | Read Status | See printers, camera, queue, library and archive (needed) |
   | Control Printer | Pause, resume, stop, temperatures, movement, filament load/unload, smart plug, SD card files |
   | Manage Queue | Add, reorder, schedule, start and remove jobs |
   | Manage Library | Upload files, slice on the server, folders, rename, move, delete and the trash |
   | Manage Archives | Favourites, notes and tags, and deleting prints from the archive |
   | Manage Inventory | Put a spool in an AMS slot; add, edit, weigh in and delete spools in Bambuddy's inventory |
   | Manage Maintenance | Mark maintenance as done |

2. Open PrintCtrl and pick **Bambuddy**. Tap **Scan the QR code from Bambuddy** and scan the code Bambuddy shows with the new key, or type the address (e.g. `bambuddy.lan:8000` or `https://bambuddy.example.com`) and paste the key.
3. Optionally add **Spoolman** too, now or later from the **You** page (tap your profile picture at the top right).

Your servers, the address in use, **Settings** and **About** are all on the **You** page behind your profile picture, on every tab.

## Notifications

Tap your profile picture › **Settings › Notifications**:

- **Progress notification:** on by default. It shows while a print runs and keeps the connection to Bambuddy open until the print ends. On Android 16 it's a **Live Update**: a progress chip in the status bar and a place at the top of the lock screen.
- **First-layer check:** on by default. Once the first layer of a print is down, a notification with a camera picture lets you check it started well, with Pause and Stop. It needs PrintCtrl to see the print start, so it works best with the progress notification on.
- **AMS drying finished:** on by default. PrintCtrl works it out itself from the AMS state (Bambuddy has no notification for it), in both alert modes below.
- **Alerts**, two ways:
  - **Check every 15 minutes:** nothing to set up on the server; alerts can be up to 15 minutes late.
  - **Instantly, through ntfy:** push comes through the **[ntfy](https://f-droid.org/packages/io.heckel.ntfy/)** app only (other push apps can't be picked). PrintCtrl still checks every hour in the background as a fallback, in case a push is missed.
    1. Install [ntfy from F-Droid](https://f-droid.org/packages/io.heckel.ntfy/) and, if you self-host ntfy, set your server as its default server. If ntfy isn't installed, PrintCtrl's notification settings show an **Install ntfy** button.
    2. In PrintCtrl choose **Instantly, through ntfy**. PrintCtrl gets its address from ntfy by itself; tap **Copy address**.
    3. In Bambuddy open **Settings › Notifications**, add a provider of type **Webhook**, paste the address as the webhook URL, keep the payload format **Generic**, and turn **attach photo** off (pictures are too big for a push message).
    4. Pick the events you want and send a test. It appears on your phone.
- **Background:** Android only lets the progress notification start with the app closed when PrintCtrl may run in the background. The notification settings show whether it can and open Android's prompt. On ColorOS (OPPO, OnePlus) also allow **auto launch** and **background activity** in the app's battery settings.

## Tips and troubleshooting

- **"Not allowed with this API key"** means the key is missing a permission. Edit the key in Bambuddy and turn on the one the action needs (see the table above).
- **Filament use** is counted by Bambuddy, which updates Spoolman after every print. PrintCtrl never takes filament off a spool by itself; weigh-ins and adjustments are only made when you confirm them.
- **Home and away:** add your LAN address first and your remote address second. PrintCtrl starts on the first address and never switches by itself, also not when your phone changes networks. Pick the address to use on the **You** page (tap your profile picture) under **Address in use**; when a server can't be reached, the Printers and Spools tabs show a button to use the other address.
- **Self-signed certificate:** PrintCtrl shows the certificate's fingerprint and asks before trusting it. Only that certificate is accepted for that address afterwards.
- **Plain HTTP** works, but the app warns you first because your API key would be sent unencrypted, and **Servers** on the **You** page marks the server "Not encrypted (HTTP)".
- **Spoolman behind a login:** Spoolman has no login of its own; if your reverse proxy asks for one, enter it under the Spoolman server.
- **Camera:** the A1 and P1 cameras send about one picture a second. The picture comes from Bambuddy, which shares a single connection to the printer.

## App lock

Turn it on: tap your profile picture › **Settings › Privacy and security › App lock**. It's off until you turn it on.

- **Its own app PIN** of at least 6 digits, separate from your phone's screen lock. Very obvious PINs such as 111111 or 123456 are refused.
- **Unlock with fingerprint** (optional) only accepts strong biometrics, such as the fingerprint sensor, checked by the phone's secure hardware. Face unlock that only uses the camera isn't accepted. The PIN is still needed after the phone restarts and at least every 72 hours, and adding a new fingerprint to the phone turns fingerprint unlock off until you turn it on again.
- **Your API key and logins are encrypted with the PIN while PrintCtrl is locked** (with notifications and the progress notification off, see below). The key only exists in memory while the app is unlocked and is wiped the moment it locks, and the connections to your servers drop them too. The PIN itself is never stored: it's stretched with 600,000 rounds of PBKDF2 and mixed with a key held in the phone's secure hardware, so a copy of the app's files isn't enough to guess it on a computer.
- **Wrong PINs:** after 5 tries you wait 30 seconds, doubling up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't skip the wait.
- **Lock again:** as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts. Picking a file to upload, saving or restoring a settings backup, opening a project link, ntfy and Android's notification and battery settings don't count as leaving, unless you stay away for more than 5 minutes.
- **Change app PIN** asks for your current PIN first, and turning the app lock off asks for the PIN too.
- **Forgot your PIN?** It can't be recovered, because it encrypts your API key and logins. The lock screen offers to remove Bambuddy and Spoolman with their API key and logins, and the printer data, pictures and notifications kept on the phone, so you can set them up again; nothing changes on your servers or printers.
- **Notifications and the app lock:** alerts, the progress notification and the first-layer check need to reach Bambuddy while PrintCtrl is locked. With any of them on, your API key and logins stay encrypted with the Android Keystore only, as without the app lock, and they keep working. Turn notifications and the progress notification off to keep them encrypted with your PIN too. Background checks keep running while PrintCtrl is locked.
- **Notifications don't name anything while the app lock is on:** no printer, job, file or spool name and no camera picture, even when the phone itself is unlocked. They say what happened ("Print finished", "Filament ran out"), and the progress notification keeps the percentage, layers and time left. Tapping one still opens that printer, behind the lock screen.
- **Pause, Resume and Stop in a notification open PrintCtrl** while the app lock is on, so they ask for your PIN first; you pause, resume or stop on the printer's page. Without the app lock, Pause and Resume work straight from the notification (after you unlock the phone), and Stop always opens PrintCtrl and asks first.
- **The widget and the Quick Settings tile** say "PrintCtrl is locked" while it's locked, including once you've been away longer than the lock timeout, instead of the printers, jobs, progress and job picture. Tapping them opens PrintCtrl's lock screen.

**Hide in screenshots** and **Hide in recent apps** (the last two switches in the same section, also without the app lock, both off by default): the first keeps PrintCtrl out of screenshots and screen recordings (and blanks its recent-apps preview), the second shows a blank card for PrintCtrl in the recent-apps view while screenshots still work. With the app lock on, the recent-apps view always shows a blank card. The server setup screen, the lock screen and the PIN screens are always kept out of screenshots.

While the lock, PIN or server setup screen or a "can't be undone" confirmation is showing, other apps' floating windows (chat heads, overlays) are hidden so nothing can cover it, and apps that read the screen through accessibility can't see the PIN or lock screen; real accessibility tools such as screen readers still work, and password managers can still fill the server setup screen.

## Privacy

**Telemetry-free.** PrintCtrl has no analytics, crash reporting, usage stats or tracking of any kind. It only talks to the servers you add.

- **Servers:** your Bambuddy and Spoolman servers, nothing else, and never the printers directly. Push notifications reach the phone through your own ntfy server and the ntfy app, the only push app PrintCtrl works with; PrintCtrl doesn't connect to ntfy itself.
- **On the phone:** API keys and proxy passwords are encrypted with a key kept in the Android Keystore (in StrongBox, the separate security chip, on phones that have one, for keys made since 1.5.11), and PrintCtrl is left out of Android backups.
- **Answers are size-limited:** a server that sends a huge answer or picture can't make PrintCtrl run out of memory; the answer is refused with a message.
- **On-screen privacy:** the screen where you type an API key or password always blocks screenshots; **Hide in screenshots** and **Hide in recent apps** (both off by default) cover the rest of the app.
- **Links:** the Support, Report a bug, Source code and Licence rows in About, and a project's link or a part's shop link, open in your browser only when you tap them; the app itself doesn't contact GitHub or those sites.
- **No update check:** PrintCtrl never checks for updates by itself; Obtainium or the F-Droid client does that if you use one.

**Google-free.** No Google Play services, Firebase, ML Kit or other Google libraries: QR codes and labels are read on the phone with open-source code. Nothing ever goes to Google. Every build checks its dependencies for them, and release builds write nothing about you to the phone's log.

## How PrintCtrl is made

PrintCtrl is a one-person project by VoidedKN0X (VKNXWorks). Much of the code is written with the help of AI (Claude, by Anthropic). I decide what the app does, review the changes and use the app myself. Every change is listed in the [changelog](CHANGELOG.md), and you can read every line of code here.

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

**Dependency verification:** every library and plugin the build downloads is checked against its SHA-256 checksum in `gradle/verification-metadata.xml`, and the build stops if one doesn't match. When you change a dependency, a plugin or the Gradle version, run the **Dependency verification metadata** workflow (Actions tab, **Run workflow**), decode the file it prints, check the diff and commit it together with the change.

## Support the project

PrintCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X), or tap **Support PrintCtrl** under **About** on the **You** page (tap your profile picture at the top right). Starring the repo and [reporting bugs](https://github.com/VoidedKN0X/PrintCtrl/issues/new/choose) helps too: pick **Bug report** or **Feature request** and fill in the form. **Report a bug** in the same About section opens the issues page.

Found a security problem? Please don't open a public issue; see [SECURITY.md](SECURITY.md).

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same Material You design.

| App | What it's for |
| --- | --- |
| [AdCtrl](https://github.com/VoidedKN0X/AdCtrl) | Control your AdGuard Home DNS server |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CallCtrl](https://github.com/VoidedKN0X/CallCtrl) | Phone app: dialer, call history and in-call screen |
| [CameraCtrl](https://github.com/VoidedKN0X/CameraCtrl) | Private camera with RAW (DNG) on every lens |
| [ChatCtrl](https://github.com/VoidedKN0X/ChatCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [ClockCtrl](https://github.com/VoidedKN0X/ClockCtrl) | Alarms, timers, stopwatch and world clock |
| [ContactCtrl](https://github.com/VoidedKN0X/ContactCtrl) | Contacts: browse, edit, import and export |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [DateCtrl](https://github.com/VoidedKN0X/DateCtrl) | Calendar for the calendars on your phone and your accounts |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [GalleryCtrl](https://github.com/VoidedKN0X/GalleryCtrl) | Private, offline gallery with an encrypted vault |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [LijnCtrl](https://github.com/VoidedKN0X/LijnCtrl) | Live De Lijn bus and tram departures |
| [LumenCtrl](https://github.com/VoidedKN0X/LumenCtrl) | Private, offline photo and video editor |
| [MailCtrl](https://github.com/VoidedKN0X/MailCtrl) | Email for Gmail and Proton Mail |
| [MapsCtrl](https://github.com/VoidedKN0X/MapsCtrl) | Private, offline maps and navigation |
| [MemoCtrl](https://github.com/VoidedKN0X/MemoCtrl) | Notes on your Memos server |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| **PrintCtrl** (this app) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [RecorderCtrl](https://github.com/VoidedKN0X/RecorderCtrl) | Private, offline voice recorder |
| [RideCtrl](https://github.com/VoidedKN0X/RideCtrl) | Offline maintenance, fuel and mod log for your vehicles |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [ShutterCtrl](https://github.com/VoidedKN0X/ShutterCtrl) | Remote control and photo transfer for Canon cameras |
| [SoundCtrl](https://github.com/VoidedKN0X/SoundCtrl) | JBL headphones and speakers, without the account |
| [SumCtrl](https://github.com/VoidedKN0X/SumCtrl) | Offline calculator, unit converter and work tools |
| [SynapseCtrl](https://github.com/VoidedKN0X/SynapseCtrl) | Administer your Matrix Synapse server |
| [TalkCtrl](https://github.com/VoidedKN0X/TalkCtrl) | Cardo intercom settings, radio and updates |
| [TapoCtrl](https://github.com/VoidedKN0X/TapoCtrl) | Tapo plugs and bulbs on your home network |
| [TextCtrl](https://github.com/VoidedKN0X/TextCtrl) | Private SMS and MMS messaging |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |
| [WakeCtrl](https://github.com/VoidedKN0X/WakeCtrl) | Keep the screen awake, per app or while charging |
| [WrtCtrl](https://github.com/VoidedKN0X/WrtCtrl) | Manage your OpenWrt router |

## License

PrintCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

PrintCtrl is an independent app and isn't made by or affiliated with Bambu Lab, the Bambuddy project or the Spoolman project.

The bundled fonts are licensed under the SIL Open Font License 1.1: [Space Grotesk](https://github.com/floriankarsten/space-grotesk) (see `FONT-LICENSE-SpaceGrotesk.txt`) and [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) (see `FONT-LICENSE-JetBrainsMono.txt`). Both licence texts also ship inside the app under `assets/licenses/`.
