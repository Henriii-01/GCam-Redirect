[![Latest stable](https://img.shields.io/github/v/release/Henriii-01/GCam-Redirect?label=stable)](https://github.com/Henriii-01/GCam-Redirect/releases/latest)
[![Latest alpha](https://img.shields.io/github/v/release/Henriii-01/GCam-Redirect?include_prereleases&label=alpha)](https://github.com/Henriii-01/GCam-Redirect/releases)

# Google/Pixel Camera - Redirect

Tiny apps that simulate the Google Photos app for the Google Camera app (Pixel Camera).
Two variants, having the same package id (`com.google.android.apps.photos`)

## `gcam-redirect-stub.apk` — stub

Presence-only. Makes Pixel Camera believe Photos is installed so its built-in
swipe filmstrip works.

## `gcam-redirect.apk` — redirect

Adds a launcher settings screen where you pick a redirect target:

- [Immich](https://immich.app/)
- System default gallery
- Any custom package name (add via button, long-press to remove)

Tapping the camera preview thumbnail redirects to the chosen app. 
It will open the photo there if the app supports it (fallback: LaunchIntent).
