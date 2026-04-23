<p align="center">
  <img src="macos/assets/app-icon.png" width="128" height="128" alt="Shelf app icon">
</p>

<h1 align="center">Shelf</h1>

<p align="center">An audiobook and video player that syncs with Google Drive.<br>Stream or download. Pick up where you left off.</p>

<p align="center"><strong>macOS</strong> · <strong>iOS</strong> · <strong>Android</strong></p>

---

<p align="center">
  <img src="macos/shelf-screenshot.png" width="600" alt="Shelf — audiobook library view on macOS">
</p>

## Features

- **Google Drive sync** — point Shelf at a Drive folder and your library loads automatically
- **Stream or download** — play directly from Drive or save for offline listening
- **Audio + video** — plays audiobooks (m4b, m4a, mp3, flac) and video files (mp4, mov, mkv)
- **Background playback** — keep listening with the screen off or the app in the background
- **Chapters, bookmarks, sleep timer** — navigate long-form content with precision
- **Playback speed** — 0.5x to 2.0x, persisted across sessions
- **Cover art lookup** — searches iTunes, Google Books, and Open Library automatically
- **Library organization** — filter, sort, search, star, hide, rate, and browse by genre
- **Discover mode** — plays a random book from a random position
- **Progress export/import** — JSON backup compatible across platforms

## Platforms

### macOS

Native macOS app with mini player, multiple libraries, sidebar navigation, and keyboard shortcuts. Requires macOS 14+.

[Download DMG](https://github.com/madebysan/shelf/releases/latest) · Swift + SwiftUI + AppKit

<details>
<summary>Keyboard shortcuts</summary>
<br>

| Shortcut | Action |
|----------|--------|
| Space | Play / Pause |
| Cmd + Right | Skip forward 30s |
| Cmd + Left | Skip back 30s |
| Cmd + B | Add bookmark |
| Cmd + Shift + M | Toggle mini player |
| Cmd + R | Refresh library |

</details>

### iOS

iPhone app with background playback, lock screen controls, fullscreen video with PiP and AirPlay, and video thumbnail extraction. Requires iOS 17+.

Swift + SwiftUI + AVKit

### Android

Material Design 3 app with streaming playback, offline downloads, background service, haptic feedback, and dark mode. Requires Android 8.0+.

Kotlin + Jetpack Compose + Media3

## Project Structure

```
shelf/
├── macos/      # Swift + SwiftUI + AppKit (macOS 14+)
├── ios/        # Swift + SwiftUI + AVKit (iOS 17+)
└── android/    # Kotlin + Jetpack Compose + Media3 (API 26+)
```

## Building

**macOS:**
```bash
cd macos && open Shelf.xcodeproj
# Build and run with Cmd+R
```

**iOS:**
```bash
cd ios && open ShelfIOS.xcodeproj
# Build for device or simulator
```

**Android:**
```bash
cd android
# Open in Android Studio, add google-services.json to app/
```

## Supported Formats

**Audio:** m4b, m4a, mp3, flac

**Video:** mp4, mov, mkv, avi, webm

## Free audio to get started

[Open Culture](https://www.openculture.com/freeaudiobooks) maintains a curated list of 1,000+ free audiobooks — classics from Twain, Orwell, Austen, and more. Download the MP3s, point Shelf at the folder, and start listening.

## License

[MIT](macos/LICENSE)

---

Made by [santiagoalonso.com](https://santiagoalonso.com)
