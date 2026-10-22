# CarTV

A web browser for the Android Auto screen. Sideloaded, not in the Play Store, so Google can't pull it.

## Download

Every push to `main` builds a new APK. Latest (sign in to GitHub in your phone's browser first):
https://github.com/soykevingon/cartv/releases/latest/download/CarTV.apk

Install it the same way you installed the first one (KingInstaller if Android Auto only showed it that way).
Builds are signed with a fixed key (`app/cartv.keystore`), so updates install over the old version and keep your sign-ins.

## On the phone (CarTV app)

- **Allow location** → "Allow all the time". Used only to detect when you're parked.
- **Allow microphone** for voice search on YouTube (may or may not work through the car's mic).
- **Open browser**: sign in to YouTube/Google, find a video, tap **Send to car**.
- **Ad blocker** on/off.
- **Home page tiles**: one per line, `Name | website`.

## On the car screen

- **☰ floating button**: Home, Search (on-screen keyboard), Back, Reload, Move button.
- **Move button**: tap it, then tap where you want the button to live.
- The small ☰ in the corner is required by Android Auto; it opens the same menu.
- Tap to click, drag or use the arrows to scroll, pinch to zoom.
- Video blocks itself above ~11 km/h and resumes after 5 seconds stopped.

## Limits

- **Netflix won't play**: it refuses Android browsers, and its DRM video renders black on the car screen surface.
- YouTube changes its ad delivery often; if ads come back, the ad-skip script may need an update.
