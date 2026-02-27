<p align="center">
  <img src="assets/app-icon.png" width="128" height="128" alt="Shelf app icon">
</p>

<h1 align="center">Shelf</h1>

<p align="center">A native macOS player for audiobooks, lectures, and long-form listening.<br>
Browse, listen, bookmark, and pick up where you left off — from local files or Google Drive.</p>

<p align="center"><strong>Version 1.3</strong> · macOS 14+ · Apple Silicon & Intel</p>
<p align="center"><a href="https://github.com/madebysan/shelf-mac/releases/latest"><strong>Download Shelf</strong></a></p>

<p align="center">
  <img src="shelf-screenshot.png" width="600" alt="Shelf — audiobook library view">
</p>

## Features

- **Multiple libraries** — add as many folders as you want, each with independent content and playback state
- **Google Drive support** — point Shelf at a Google Drive folder and it scans for audiobooks, handling cloud-only files with smart error recovery
- **Download-then-play** — cloud-only files auto-download when clicked, with progress display and automatic metadata re-extraction
- **Multiple view modes** — browse your library as a grid, large grid, or sortable list
- **Playback** — play/pause, skip forward/back 30s, adjustable speed (0.5x–2.0x), chapter navigation, and media key support
- **Bookmarks** — mark important moments with a name and optional note, jump back to them anytime
- **Sleep timer** — 15/30/45/60 min presets or end-of-chapter, with volume fade
- **Discover mode** — plays a random book from a random position without saving progress
- **Smart Collections** — auto-grouped sidebar views: Recently Added, Short Books, Long Books, Not Started, Nearly Finished
- **Progress tracking** — playback position is saved automatically and persists across launches
- **Star / hide / rate** — star favorites, hide books from the library, rate 1–5 stars
- **Cover art lookup** — searches iTunes, Google Books, and Open Library for cover art when files lack embedded artwork
- **Import/Export** — backup and restore your progress and bookmarks as a JSON file
- **Mini Player** — floating always-on-top player that follows across desktop spaces
- **Sidebar navigation** — browse by author, genre, year, smart collection, or status

## Supported Formats

- **m4b** — AAC audiobooks
- **m4a** — AAC audio
- **mp3** — MPEG audio

## Install

Download `Shelf.dmg` from the [latest release](https://github.com/madebysan/shelf-mac/releases/latest), open it, and drag Shelf to your Applications folder.

## Free audio to get started

[Open Culture](https://www.openculture.com/freeaudiobooks) maintains a curated list of 1,000+ free audiobooks — classics from Twain, Orwell, Austen, and more. Download the MP3s, point Shelf at the folder, and you're listening.

## Build from source

Requires Xcode 15+ and macOS 14 (Sonoma) or later.

```bash
git clone https://github.com/madebysan/shelf-mac.git
cd shelf
open Shelf.xcodeproj
```

Build and run with Cmd+R in Xcode.

## Keyboard shortcuts

| Shortcut | Action |
|----------|--------|
| Space | Play / Pause |
| Cmd + Right | Skip forward 30s |
| Cmd + Left | Skip back 30s |
| Cmd + B | Add bookmark |
| Cmd + Shift + M | Toggle mini player |
| Cmd + R | Refresh library |
| Cmd + Shift + O | Add library |
| Cmd + Shift + E | Export progress |
| Cmd + Shift + I | Import progress |

## Architecture

```
Shelf/
  ShelfApp.swift            # App entry point, menu commands
  ContentView.swift         # Main window layout
  Models/
    AudiobookModel          # Core Data model (Book, Bookmark, Library entities)
    Book+Extensions.swift   # Display helpers, formatting
    Bookmark+Extensions.swift
    Library+Extensions.swift
  ViewModels/
    LibraryViewModel.swift  # Library state, scanning, filtering, import/export
    PlayerViewModel.swift   # Playback bridge, chapters, bookmarks, sleep timer
  Views/
    LibraryGridView.swift   # Grid/list library display
    BookCardView.swift      # Grid card component
    BookListRow.swift       # List row component
    PlayerView.swift        # Full player sheet
    NowPlayingBar.swift     # Bottom bar mini player
    MiniPlayerView.swift    # Floating panel player
    SidebarView.swift       # Navigation sidebar with library switcher
    ChapterListView.swift   # Chapter navigator
    BookmarkListView.swift  # Bookmark list with jump-to
    AddBookmarkSheet.swift  # New bookmark modal
    ManageLibrariesView.swift # Library management sheet
    PreferencesView.swift   # Settings window
  Services/
    AudioPlayerService.swift    # AVPlayer wrapper, Now Playing integration
    CoverArtService.swift       # iTunes/Google Books/Open Library cover lookup
    FileUtils.swift             # Cloud file detection, NSFileCoordinator downloads
    LibraryScanner.swift        # File discovery and metadata sync
    MetadataExtractor.swift     # AVFoundation metadata extraction
    MiniPlayerController.swift  # Floating NSPanel management
    PersistenceController.swift # Core Data stack
    ProgressExporter.swift      # JSON import/export
  Utilities/
    AppAnimation.swift          # Named animation curves (spring, ease)
    AppTransitions.swift        # Reusable composed transitions
    ViewModifiers.swift         # StaggeredAppear, EmptyStateAppear modifiers
```

## Feedback

Found a bug or have a feature idea? [Open an issue](https://github.com/madebysan/shelf-mac/issues).

## License

MIT
