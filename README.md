# Soundprint

Soundprint is an offline-first Android music player built with **Kotlin** and **Jetpack Compose**. It is designed for people who want to enjoy and organize the music already stored on their devices, without needing a streaming service or an account.

The goal is to bring your music library together in one clean experience, with music discovery based on your own collection.

## Features

- Local library: import folders or individual files; the original audio files are never modified or deleted.
- Background playback with a media notification, lock-screen controls, queue restore, gapless playback and optional crossfade.
- Background music import that keeps running when you rotate the phone or leave the app.
- Albums, artists, playlists and smart playlists, plus listening statistics.
- Mood discovery: on-device audio analysis (CLAP model) suggests music for a mood. No audio leaves the device.
- Twinsense: finds duplicate and alternate versions of tracks with on-device audio fingerprints. Removed tracks go to a Recycle Bin for 30 days.
- Optional lyrics lookup through LRCLIB. This is the only feature that uses the network, and it sends the song title, artist, album and length.

## Project status

Soundprint is under active development. Features and the app experience are still being built.

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- AndroidX Media3 (ExoPlayer, MediaSession)
- WorkManager for durable background imports
- SQLite (write-ahead logging) for the library
- ONNX Runtime for on-device audio analysis
- Google Play Asset Delivery for the model files

## Building

```
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The first build downloads the CLAP model files from Hugging Face and verifies their checksums. Release builds ship the models as an on-demand Play asset pack (`./gradlew bundleRelease`).

## App icon

The launcher icon is an adaptive icon (background, foreground and monochrome layers) in `app/src/main/res/mipmap-*`. The 512 px Play Store version is `app/src/main/ic_launcher-playstore.png`.
