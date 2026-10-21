# CarTV

A web browser for the Android Auto screen. Sideloaded, not in the Play Store, so Google can't pull it.

## Build the APK (no computer needed)

1. Create a free GitHub account and a new **private** repository named `CarTV`.
2. Upload the contents of this folder (keep the `.github` folder, it holds the build script).
3. Open the repo's **Actions** tab. The "Build APK" run starts on its own (or tap *Run workflow*).
4. When it finishes (about 5 minutes), open the run and download **CarTV-apk**. Unzip it to get `app-debug.apk`.

## Install on the phone

1. Open the APK and allow installs from your browser/files app when asked.
2. Open **CarTV** on the phone and tap **Allow location**, then choose **Allow all the time**.

## Turn on unknown sources in Android Auto

1. Phone Settings → search **Android Auto** → open its settings.
2. Scroll to the bottom and tap **Version** about 10 times until it says developer settings are on.
3. Menu (⋮) → **Developer settings** → enable **Unknown sources**.
4. Reconnect to the car. CarTV appears in the Android Auto app launcher.

## Using it

- Tap the page to click. Drag (or use the arrows) to scroll; pinch to zoom.
- **Go** opens a search box plus shortcuts. **Back**, **Home**, **Reload** do what they say.
- If drag-scroll doesn't respond, tap the pan (crosshair) button first.
- Video blocks itself when GPS speed goes above ~11 km/h and resumes after 5 seconds stopped.

## Limits

- **Netflix won't play.** It needs DRM (Widevine) that an embedded browser doesn't provide.
- The car's keyboard is disabled by Android Auto while moving.
- A big Android Auto update could break the unknown-sources setting; re-check it if CarTV disappears.
