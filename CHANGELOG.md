# Changelog

What changed in each PrintCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/PrintCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

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
