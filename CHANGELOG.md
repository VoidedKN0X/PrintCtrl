# Changelog

What changed in each PrintCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/PrintCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## Unreleased

### Changed

- **README**: a Screenshots section (screenshots are coming soon), and a link preview image (`.github/assets/social-preview.png`) with PrintCtrl's icon, name and summary, for when the repo is shared on Reddit, Discord or elsewhere

## v1.0.2 (2026-10-19)

### Changed

- **One About section in every Ctrl app**: Support, Report a bug, Source code and Licence (GPL-3.0), with the version underneath, at the bottom of the Account page (tap the profile button at the top right)
- New **Report a bug** row in About opens PrintCtrl's GitHub issues, where you can report a problem or suggest an idea
- The "Source code and issues" row is now just **Source code**, since bugs have their own row

## v1.0.1 (2026-10-19)

### New

- **Support PrintCtrl** under **About** at the bottom of the Account page (tap the profile button at the top right) opens VKNXWorks' GitHub Sponsors page in your browser. PrintCtrl is free and open source, and sponsoring helps keep it that way
- **Source code and issues** in the same **About** section opens PrintCtrl's GitHub repository, where you can read the code or report a bug
- **Licence** in the same section opens the GNU General Public License v3.0 that PrintCtrl is released under

## v1.0.0 (2026-10-19)

PrintCtrl 1.0: managing your print history and library, projects, printer charts, and Bambuddy's own spool inventory for setups without Spoolman.

### New

- **Favourite prints.** Star or unstar a print from its page. Starred prints show a star in History, and a new **Favourites** chip shows only them
- **Notes and tags on prints.** A print's page has an **Edit** button for its notes and tags; tags you've used before are offered to pick from. Tags show on the print's page and in the History list
- **Delete a print.** PrintCtrl first asks Bambuddy what goes with it and tells you how many queue entries are removed too; a print that's running from the queue right now can't be deleted. You choose whether it also leaves the statistics (otherwise its filament, time and cost keep counting). History updates by itself afterwards
- **Library folders.** Browse into folders with a tappable path at the top (Back goes up a folder), and create, rename and delete folders (deleting asks first and warns that everything inside goes too). Read-only NAS folders can't be changed
- **Manage library files.** Each file has a ⋮ menu to **Rename** (the extension is kept), **Move** to another folder or the top of the library, or **Move to trash**. Search looks through every folder and shows which folder a file is in, and uploading while a folder is open puts the file there
- **Library trash** (button at the top of the library): deleted files with their size, the folder they came from and when Bambuddy removes them for good. Restore a file, delete it for good, or empty the trash; anything permanent asks first
- **Projects.** A new button at the top of the Queue tab opens Bambuddy's projects, filtered by Active, Completed, Archived or All. Each card shows the cover picture and colour, status, priority, progress towards the target, prints done, failed and queued, sub-projects and the due date ("Overdue since …" when an active project is late)
- **A project's page:** progress towards the print and parts targets; an overview with what's done, failed, printing and queued, print time, filament, filament and electricity cost, parts to buy and spend against the budget; notes, tags and the project link; sub-projects; its library files with how often each was printed (tap to print one); its prints (tap to open); the parts list with quantities, prices and where to buy; and a timeline. Creating and editing projects isn't in the app yet; do that on Bambuddy's web page
- **Temperature history.** A new row on the printer page opens charts of the nozzle and bed (and chamber or second nozzle on printers that have them) for the last 1 hour, 6 hours, 24 hours or 7 days, with the target as a dashed line, the lowest, average and highest reading, a temperature scale and times. AMS units with a humidity sensor (AMS, AMS 2 Pro, AMS HT) get humidity and temperature charts too. Pull down to refresh; gaps where the printer was off are left open
- **Pressure advance.** A new row on the printer page lists the K-factor calibrations stored on the printer: pick the nozzle size, see each profile's K value, filament, linked AMS slot and any note from Bambuddy, grouped by nozzle type. Read-only, because Bambuddy only lets an admin change them
- **Firmware updates.** The Firmware row on the printer page now says whether the printer is up to date or which update is available (as Bambuddy checks with Bambu Lab). Tap it for the release notes and how to update on the printer or in Bambu Handy. PrintCtrl never starts a firmware update itself
- **Spools without Spoolman.** With only Bambuddy set up, the Spools tab now shows the spools in Bambuddy's own inventory, with search, a material filter, archived spools and "Running low" marking. If Bambuddy keeps its spools in Spoolman, the tab shows those through Bambuddy, with a hint that adding Spoolman itself gives live weights, label scanning and SpoolmanDB
- **A spool's page (Bambuddy inventory):** what's left, the AMS slot it's in, filament details, location, empty-spool weight, price per kg, RFID tag and notes. **Weigh in** (shows the result before saving, and records the reading and when you weighed), set what's left as a correction (both wait while the spool's printer is printing, since Bambuddy adds that print's usage when it finishes and it would otherwise be counted twice), edit the brand, material, type, colour, location, price and note, put it in an AMS slot or take it out, and archive, restore or delete it (asks first)
- **Add a spool to Bambuddy's inventory:** brand, material, type, colour name and colour (with a swatch), full and empty spool weights, what's left (full by default), price per kg, location and note
- **Bambuddy's inventory everywhere spools appear:** "Put a spool in this slot" and "Take the spool out" on the printer page, the not-enough-filament warning when sending a print, and low-stock alerts (tapping one opens the spool) all work with Bambuddy's inventory when Spoolman isn't set up. The "Spools running low" switch in Notifications now shows for these setups too

- **Back up and restore settings** (Account › Settings › Backup). Saves a file wherever you choose with your app settings and your servers (addresses, which address is in use, trusted certificates). API keys, passwords and proxy logins are never included, and neither is your usage-stats choice. Restoring checks the file and asks first; a server already set up at the same address keeps its API key and login, otherwise PrintCtrl tells you which server needs its key entered again and opens its setup
- **Wide screens.** In landscape, on tablets and on unfolded foldables, printer cards sit side by side, and lists and pages keep a comfortable reading width instead of stretching edge to edge. On a wide screen held sideways, the printer page shows the camera on the left with the rest of the page scrolling beside it

### Changed

- The camera on the printer page now tells screen readers that tapping it opens it full screen
- When an action isn't allowed with your API key, the message now names the exact permission to turn on in Bambuddy ("Manage Archives", "Manage Library", "Manage Inventory", "Read Status")
- A print's page puts its action buttons in a row that scrolls sideways, so all of them fit on narrow phones

PrintCtrl still never takes filament off a spool by itself: every change to a spool is one you make and confirm.

## v0.5.0 (2026-10-19)

PrintCtrl no longer picks between your two server addresses on its own: you choose which one it uses. It is also fully Google-free now.

### Changed

- **You choose the address.** If you saved two addresses for Bambuddy or Spoolman (for example home and remote), pick the one PrintCtrl uses under **Account › Address in use**. Each server has its own choice, and PrintCtrl stays on it until you change it, also when your Wi-Fi, mobile data or VPN changes
- **After updating, PrintCtrl uses each server's first address.** Your second address is still saved; pick it under **Address in use** when you want it
- **Editing a server keeps your choice.** Changing a server's key or addresses keeps using the address you picked, as long as it's still one of the saved addresses; otherwise PrintCtrl goes back to the first one
- **"Can't connect" offers the other address.** When the Printers or Spools tab can't get through to a server that has a second address, a bar says which address is in use with a **Use first address** or **Use second address** button. Nothing changes until you tap it
- **Account › Servers** shows the address in use right away instead of "Connecting…" while addresses were being tried
- The help text under **Second address** on the setup screen now explains the manual choice
- **README** brought up to date with v0.4.4: the feature list mentions the nozzle and bed temperature graphs on a printer's page, choosing the currency for print costs, and which printer actions ask before they happen (stop, resume, homing, loading and unloading filament, skipping parts, "plate cleared" and starting a queued job). New bullets describe what PrintCtrl keeps off the lock screen and the recent-apps view, that Pause and Resume in a notification need an unlocked phone, and that files shared to PrintCtrl are only uploaded after you confirm. Requirements say the camera (QR codes) and NFC (spool tags) are optional, and the tips mention the "Not encrypted (HTTP)" mark under Account › Servers

### Removed

- **Automatic address switching.** PrintCtrl no longer tries your addresses one after the other to see which answers, and no longer checks again whenever the phone switches networks

### Privacy

- **Google-free.** PrintCtrl no longer asks Google Play services for an emoji font when it starts; emoji use your phone's own font. PrintCtrl uses no Google Play services, Firebase or Google servers
- **Your API key and proxy login only go to the address you picked.** PrintCtrl no longer sends test requests to your other saved address in the background

## v0.4.4 (2026-10-19)

A security and privacy check of the whole app. Nothing you rely on goes away; a few printer actions now ask before they happen, and less shows on the lock screen.

### Security

- **Pause and Resume in notifications need an unlocked phone.** The buttons on the print progress notification and the first-layer picture used to work straight from the lock screen, so anyone holding your phone could pause or restart a print. Android now asks you to unlock first, then carries out the tap
- **Resuming a print asks first.** Tapping Resume on a printer's page shows "Resume the print?" and a reminder to keep hands and tools away from the print head, so a stray tap can't set the printer moving
- **"Plate cleared" asks first.** Confirming the plate is clear can make Bambuddy start the next queued job straight away, so the button now asks you to check that the build plate is empty and back in place
- **Starting a queued job asks first.** "Start now" in the Queue tab shows "Start … now?" with the same plate check before the job is sent to the printer
- **Unloading filament asks first,** like loading already did: the printer heats the nozzle to pull the filament out
- **Other apps can't open the "Stop the print?" question.** Only the Stop button in PrintCtrl's own notifications opens a printer's page straight into that question. Other apps can still open PrintCtrl, but not with that question already showing
- **Shared files are only uploaded after you agree.** Sharing a 3MF, G-code or STL to PrintCtrl (or opening one with it) now asks "Upload to Bambuddy?" with the file name first. Before, the upload started straight away
- **Only files from other apps are accepted.** PrintCtrl ignores shared file addresses that point into its own storage or at a plain file path, so another app can't trick it into uploading PrintCtrl's own settings to your server. Web pages can no longer hand files to PrintCtrl either
- **The Quick Settings tile asks to unlock the phone** before it opens a printer's page from the lock screen
- **Camera frames have a size limit.** A broken or hostile stream that claims a frame of hundreds of megabytes is dropped and reconnected instead of making the app run out of memory and close
- **Files saved from the SD card get a safe name.** Folder parts, control characters and leading dots are removed from the name before the file goes into Download/PrintCtrl
- **Large uploads no longer load the whole file into memory.** Files you upload to the library are streamed from the phone, so a very large file can't crash the app

### Privacy

- **Less on the lock screen.** When your lock screen hides sensitive content, PrintCtrl's notifications now show only what kind of notification it is ("Print running · 45%", "First layer done", "Printer update", "Filament running low"), without printer or job names and without the camera picture. With full contents allowed on the lock screen they look the same as before
- **No PrintCtrl screen in the recent-apps preview.** Android's app switcher shows a blank card for PrintCtrl instead of a snapshot of your camera, prints or the API key on the setup screen. Before, only the server setup screen was hidden
- **Removing a server removes what the app kept from it.** Removing Bambuddy also clears saved print covers and thumbnails, downloaded timelapses, the widget and Quick Settings tile contents, remembered print states and its notifications; removing Spoolman clears its low-stock records and cached pictures
- **The push address is copied as sensitive.** Anyone who has your ntfy push address can send notifications to your phone, so "Copy address" now marks it as sensitive: Android hides it from the clipboard preview and keyboards don't keep it in their clipboard history
- **Push messages are kept short.** A notification made from a push message shows at most 500 characters of title and text
- **Usage stats stop at once when switched off.** If you turn off usage stats, anything still waiting to be sent is thrown away immediately. Each time you open the app is now a new stats session, even if Android kept PrintCtrl running in the background in between (this only matters if you opted in)

### Changed

- **Servers on plain HTTP are marked.** Under Account › Servers, a server you reach over HTTP now says "Not encrypted (HTTP)" next to its address, as a reminder that its API key or login travels unencrypted
- **All Ctrl apps in the README.** The README's Ctrl series table now lists all 14 apps. New in the table: [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) (Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app), [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) (scan and browse documents on Paperless-ngx), [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) (manage your Proxmox VE server) and [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) (monitor and manage your TrueNAS server). Every app in the table is also in the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) for automatic updates. No change to the app itself.

## v0.4.3 (2026-10-19)

### New

- **Optional anonymous usage stats.** After you set up Bambuddy or Spoolman, PrintCtrl asks once whether you'd like to share anonymous usage stats. Nothing is sent unless you tap **Share stats**, and you can switch it on or off any time in **Account › Settings › Usage stats**
- **What's shared:** which screens you open (for example "Printers", "Printer", "Spool" or "Settings", never which printer, spool, file or print), whether Bambuddy, Spoolman or both are set up, your notification mode, which print controls you use (pause, resume or stop, and whether from the app or a notification), which kinds of spool changes you make (weigh, adjust, edit, link an NFC tag, archive, add, put in or take out of an AMS slot), the app version, Android version, phone model, language, and phone or tablet
- **What's never shared:** your server addresses, API keys or logins, printer names or serial numbers, file or job names, spool details such as filament, colour, weight or location, or any ID that follows you between sessions. Each session gets a new random ID, and stats from before and after you switch them off and on again aren't linked
- **Where it goes:** to VKNXWorks' own self-hosted server (Aptabase), not to Google or any analytics company. Builds you make from source can't send stats at all, and the setting doesn't appear in them
- The README has a new **Usage stats** section listing exactly what's collected

## v0.4.2 (2026-10-19)

### New

- **Choose the currency for print costs.** Account › Settings › Costs › Currency shows costs in Statistics and on a print's page in the currency you pick (euro, US dollar, pound, Swiss franc, Nordic and Central European currencies and more), written the way your phone writes money. "Same as Bambuddy" (the default) keeps using the currency set in Bambuddy. Amounts aren't converted: Bambuddy works out costs from the prices you entered there, so this only changes the symbol. Handy when Bambuddy is still on its default of US dollars but your prices are in another currency

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
