# Changelog

What changed in each PrintCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/PrintCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.4.1 (2026-10-19)

### Changed

- **The camera opens faster.** One camera stream per printer is now shared by the whole app, so opening the camera full screen from the printer page continues the picture that's already running instead of starting a new connection. After you leave the camera, the stream stays open for another 30 seconds, so going back to the printer (or into full screen again) shows a live picture right away, and Bambuddy keeps its connection to the printer open, which is the slow part to start
- **The last picture shows straight away.** Opening a camera you've watched before shows its last picture at once with a "Connecting…" label, instead of a black box with a spinner, until the live picture takes over
- **Quicker start and reconnect.** The camera's access token is fetched as soon as Bambuddy is reachable rather than when you open the camera, and a dropped stream reconnects after half a second the first time instead of three seconds

### Fixed

- **The camera kept reloading when you scrolled the printer page.** Scrolling the camera out of view stopped the stream, so it had to load again when you scrolled back up. The stream now keeps running for as long as the printer page is open

## v0.4.0 (2026-10-19)

### New

- **Live Update on Android 16.** The print progress notification is now an Android 16 Live Update: a chip in the status bar showing the percentage ("Starting" while the printer gets ready, "Paused" while paused), and a spot at the top of the lock screen and notification shade with a progress bar the printer icon rides along, amber while paused. Phones that show Live Updates (including ColorOS 16) pick it up automatically. If you've turned Live Updates off for PrintCtrl, Notifications › While printing says so and opens the setting. Older Android versions keep the normal progress notification
- **First-layer check.** Once the first layer of a print is down, a notification shows a camera picture of the bed so you can spot a print that isn't sticking or is going wrong, with **Pause** and **Stop** right on it (Stop still asks first). It comes once per print, only while the print is still near its start (a print PrintCtrl first sees further along is skipped rather than reported late), and is cleared when the print ends. It needs PrintCtrl to see the print starting, so it works best with the progress notification on. On by default; turn it off under Notifications › While printing › First-layer check
- **What your prints cost.** Bambuddy already works out each print's filament cost from your spool or filament prices, and its electricity cost from a smart plug. PrintCtrl now shows them:
  - **Statistics:** "Filament cost" and, with a smart plug, "Electricity cost" tiles for everything printed, plus **Cost per month** for this month and the five before it. Reprints count every time, just like Bambuddy's own statistics
  - **A print's page** shows its filament and electricity cost (with the kWh used)
  - Amounts are in the currency set in Bambuddy, written the way your phone writes money. Prices and the currency are set in Bambuddy's settings; the cost lines only appear once Bambuddy has costs to show

## v0.3.0 (2026-10-19)

### New

- **Pick a spool right from an AMS slot.** Tap a slot on the printer page and choose "Put a spool in this slot": a list of your Spoolman spools opens, with spools of the slot's material first and a search box. Picking one links it to the slot through Bambuddy, which also sets the slot's filament on the printer. **Take the spool out** unlinks it again
- **Enough filament?** When you send a file or an earlier print to a printer, each filament now warns in red if the spool linked to its slot has less left than the plate needs (by Spoolman's count), for example "Bambu PLA Basic Black has 42 g left; this needs 87 g"
- **SD card files** on the printer page: browse the folders on the printer's card, see how much space is used, save any file to the phone's Download/PrintCtrl folder, and delete files (asks first). Goes through Bambuddy and needs "Control Printer" on the API key; the app says so if it's missing
- **Filament used by material** in Statistics: grams (or kilograms) per material, using what Bambuddy measured where it could and the slicer's estimate for successful prints otherwise. Prints with more than one material are listed under their combination, such as "PLA + PETG". Counts your latest 2000 prints
- **Low-stock alerts:** a notification when a Spoolman spool drops below your "warn below" amount (Settings › Spools). Each spool alerts once, and again only after it's been back above the threshold (refilled or weighed in). Several at once become one summary notification. Tapping opens the spool (or the Spools tab). Checked with the background alerts and straight after a print ends; the first check, and the first after changing the threshold, only takes note of spools that are already low so you aren't flooded. Turn it off under Notifications › Tell me about › Spools running low
- PrintCtrl is now in the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/), with automatic updates in F-Droid, Droid-ify and Neo Store

### Changed

- "Prints by material" in Statistics is now labelled as such, next to the new grams-per-material list

## v0.2.0 (2026-10-19)

The rest of the app: notifications, spool editing with QR and NFC, the print queue, print history, a home-screen widget and a Quick Settings tile, plus more printer controls.

### New

- **Queue and Archive tabs.** With Bambuddy set up, the bar at the bottom now has Printers, Queue, Archive and (with Spoolman) Spools
- **Print progress notification** while a print runs: progress bar, layers, time left, the clock time it'll be done, a camera picture refreshed every two minutes, and Pause/Resume. Stop opens the app and asks first. It stays live for the whole print, even prints longer than six hours, and goes away a minute after the print ends. Turn it off under Account › Settings › Notifications
- **Alerts** for a finished print, a failed or stopped print, a paused print, filament runout, printer errors, AMS problems and AI failure detection, each with its own switch. Two ways to get them, neither using Google services:
  - **Check every 15 minutes:** the phone asks Bambuddy in the background; nothing to set up on the server
  - **Instantly through ntfy (UnifiedPush):** pick the ntfy app, copy the address PrintCtrl shows and add it to Bambuddy as a Webhook notification provider. Bambuddy's own events (finished, failed, errors, AMS, AI detection) arrive straight away; pauses and runout are still picked up by the app itself because Bambuddy has no event for them. Bambuddy's test message shows up too, so you can check the setup
- **Background guide** in the notification settings: shows whether battery optimisation limits PrintCtrl, opens Android's prompt to allow it, and explains the extra ColorOS steps (auto launch, background activity)
- **Queue tab:** Bambuddy's queue with each job's picture, printer, estimated time and filament. Move jobs up and down or to the top, set or clear a start time, start a job that waits for you (with a warning if Bambuddy thinks there isn't enough filament), and remove jobs. Refreshes on its own every 20 seconds
- **Library and printing a file:** browse and search Bambuddy's file library, or upload a file from the phone. Pick the plate (with pictures for multi-plate files), the printer and which AMS slot feeds each filament; slots are matched automatically by material and colour and can be changed. Options for a timelapse, waiting for a manual start, and a start time
- **Slice on the server:** a model that isn't sliced yet can be sliced by Bambuddy's OrcaSlicer or Bambu Studio sidecar, choosing the printer, process and filament profiles, then printed. Only offered when server slicing is turned on in Bambuddy
- **Share to PrintCtrl:** share a 3MF, G-code or STL from another app (or open one with PrintCtrl) to upload it to the library and go straight to printing it
- **Archive tab:** every print Bambuddy recorded with its picture, result, date, time, filament and printer. Search, and filter by result (successful, failed, cancelled) and by printer. A print's page shows how long it took against the estimate, filament, layers and notes, with **Print again** and its **timelapse** when there is one
- **Statistics:** total prints, success rate, print hours, filament used, prints per week for the last eight weeks, and prints per material
- **Spools tab, now complete:**
  - Search, filter by material and location, sort (material, least left, most left, recently used, newest) and show archived spools
  - Live: weights change on screen as Bambuddy reports use during a print
  - A **running low** mark on spools under a threshold you choose (150 g by default, under Settings)
  - **Spool page** with what's left, what it started with, metres left, filament details and temperatures, location, lot number, empty spool weight and NFC tags
  - **Weigh-in:** put the spool on a scale and enter the total; PrintCtrl shows what's left before saving. **Adjust:** remove or add back filament by hand. Both always ask first, and PrintCtrl never takes filament off a spool by itself (Bambuddy already does that for prints)
  - Edit location, lot number and comment; archive and restore spools
  - **Add a spool:** from a filament you already have, from SpoolmanDB (adds the vendor and filament for you), or a new filament you describe
  - **Put a spool in an AMS slot** through Bambuddy, which also sets the slot's filament on the printer
- **Scan a spool:** the scan button reads Spoolman QR labels (WEB+SPOOLMAN:S-… and …/spool/show/… links) with the camera, and NFC tags held to the phone, and opens the spool. Tags are looked up in Spoolman, then in Bambuddy (which knows Bambu RFID tags). **Link NFC tag** on a spool's page links a tag to it. QR reading uses zxing, which is fully open source
- **Printer page additions:**
  - Tap an AMS slot to load or unload its filament, read its RFID tag again, or see which Spoolman spool is in it. Linked spools are named on the slot tiles
  - Tap the nozzle or bed graph to set its target temperature, or turn the heater off
  - **Move** panel while idle: home (asks first), jog X/Y and the bed by 1, 10 or 50 mm, extrude and retract
  - **Skip parts** of the running print (asks first)
  - **Smart plug** from Bambuddy: on/off state, power draw and today's energy, with a switch. Turning it off asks first and warns if a print is running
  - **Maintenance** from Bambuddy: each item with how close it is to due, and Done to log it
  - **AI failure detection** verdict while printing, when Bambuddy's Obico detection watches the printer
- **Scan Bambuddy's QR code** in the Bambuddy setup: the code Bambuddy shows when you create an API key fills in the address and key
- **Home-screen widget** with each printer's state, progress, when it'll be done and the job's picture. It resizes: wider adds the picture, taller shows two printers. Tap a printer to open it
- **Quick Settings tile** with the running print's progress; tap it to open that printer

### Changed

- The notification, widget and tile open the printer they're about
- Notification settings and a "warn below" setting for spools are new under Account › Settings

### Security

- The push receiver only accepts messages carrying a random secret that PrintCtrl gave the push app; switching push off throws the secret away, so the old address stops working
- Pause and resume from the notification can only be triggered by PrintCtrl's own notification

## v0.1.1 (2026-10-18)

### Fixed

- **Connecting to Bambuddy failed with "Not found on the server"** even with a correct address and API key. The app asked for the printer list at an address without the trailing slash Bambuddy expects, and Bambuddy answers that with "not found" instead of correcting it. The connection test, the Printers tab and the live updates now reach your printers. Every address the app uses was checked against Bambuddy 1.2.5.7

## v0.1.0 (2026-10-18)

The first release: server setup and the Printers tab with live status and camera. Notifications, spool editing with QR and NFC, the print queue, print history and home-screen widgets come in later versions.

### New

- **Connect Bambuddy, Spoolman or both.** Each server is optional: on first start you pick the one you use, and you can add, change or remove either later from the account page (the profile button at the top right). The tabs at the bottom match the servers you've set up
- **Connection test before saving.** PrintCtrl checks that the address really is Bambuddy or Spoolman and that Bambuddy accepts your API key, and tells you plainly what's wrong: the server can't be found, the connection was refused, the key was refused, the key is missing a permission, or the certificate isn't trusted
- **Two addresses per server**, such as your home address and the one you use away from home. PrintCtrl uses the first that answers and checks again whenever your phone changes networks
- **Self-signed certificates** can be trusted per server after checking their SHA-256 fingerprint. Only that exact certificate is accepted for that address afterwards, and you're warned if it ever changes. There is no "trust everything" switch
- **Plain HTTP** is possible for servers on your home network, after a warning that your API key would be sent unencrypted
- **Printers tab** with a big card for each printer in Bambuddy: its state, the job name and picture, progress, current and total layers, time left and the clock time it'll be done, nozzle and bed temperatures, the filament in each AMS slot, and any printer messages
- **Live updates** over Bambuddy's live connection while the app is open; a "Live" badge shows when it's connected. If it can't connect, the printers are refreshed every 10 seconds instead. Pull down to refresh at any time
- **Printer page** with everything about one printer:
  - **Live camera** through Bambuddy's shared camera stream, so it never takes the printer's only camera connection away from Bambuddy. Tap it for full screen with pinch to zoom, drag to look around, double-tap to reset, and a button to save the current picture to Pictures/PrintCtrl
  - **Job details** including how long the print has been running
  - **Controls:** pause, resume, stop (always asks first), print speed (Silent, Standard, Sport, Ludicrous) and the light. Controls only work while they make sense, and a refused command explains which API key permission is missing
  - **Plate cleared** button when Bambuddy's queue is waiting for you to clear the plate before starting the next job
  - **Printer messages (HMS)** marked as error, warning or notice, with Bambu's explanation, the full HMS code, and a button to clear them
  - **Temperature graphs** for the nozzle and bed over the last 15 minutes, with the target as a dashed line
  - **AMS and AMS lite** slots with filament colour, material, the share left (for spools with a Bambu tag) and the slot in use. Humidity only appears on units that report it, so it stays hidden on the AMS lite. The external spool shows too
  - **Fans** with a slider for each fan the printer reports (part cooling on the A1; more on other models)
  - Model, firmware and Wi-Fi signal
- **Spools tab** listing your Spoolman inventory with colour swatches (multi-colour filament shows stripes), vendor, filament, material, grams left with a bar, location and spool number
- **Settings:** camera frame rate, keep the screen on in full-screen camera, theme (follow system, dark, light, pure black) and wallpaper colours
- Every text in the app is in one translatable file, ready for other languages

### Security

- Your Bambuddy API key and any Spoolman proxy password are stored encrypted with a key kept in the phone's secure hardware, and the app's data is excluded from backups
- The API key is only ever sent to the server it belongs to. If a server redirects to another site, the key, any login and the camera token are removed first, and requests with data are never passed on
- An HTTPS connection is never quietly switched to plain HTTP
- The setup screen is hidden from the recent-apps preview while a key might be visible
