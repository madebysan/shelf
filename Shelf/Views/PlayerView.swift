import SwiftUI

/// Full player view shown as a sheet — cover art, controls, chapters
struct PlayerView: View {
    @EnvironmentObject var playerVM: PlayerViewModel
    @Environment(\.dismiss) var dismiss

    var body: some View {
        VStack(spacing: 0) {
            // Header with close button
            HStack {
                Spacer()
                Button { dismiss() } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title3)
                        .foregroundColor(.secondary)
                }
                .buttonStyle(.plain)
                .padding()
            }

            // Discover mode banner
            if playerVM.isDiscoverMode {
                HStack {
                    Image(systemName: "shuffle")
                        .font(.caption)
                    Text("Discover Mode")
                        .font(.caption)
                        .fontWeight(.medium)
                    Text("-- progress not saved")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Spacer()
                    Button("Next") {
                        playerVM.discoverRandomBook()
                    }
                    .font(.caption)
                    .fontWeight(.medium)
                    Button("Exit") {
                        playerVM.exitDiscoverMode()
                    }
                    .font(.caption)
                }
                .padding(.horizontal)
                .padding(.vertical, 6)
                .background(Color.accentColor.opacity(0.1))
                .transition(.slideAndFade)
            }

            // Main content — vertical layout
            ScrollView {
                VStack(spacing: 20) {
                    // Cover art + info
                    if let book = playerVM.currentBook {
                        Image(nsImage: book.coverImage)
                            .resizable()
                            .aspectRatio(1, contentMode: .fill)
                            .frame(width: 200, height: 200)
                            .clipped()
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .shadow(color: .black.opacity(0.2), radius: 8, y: 4)

                        VStack(spacing: 4) {
                            Text(book.displayTitle)
                                .font(.title3)
                                .fontWeight(.semibold)
                                .lineLimit(2)
                                .multilineTextAlignment(.center)

                            Text(book.displayAuthor)
                                .font(.body)
                                .foregroundColor(.secondary)
                                .lineLimit(1)

                            if let chapter = playerVM.currentChapterName {
                                Text(chapter)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .padding(.top, 2)
                            }
                        }
                    }

                    // Scrubber
                    VStack(spacing: 4) {
                        Slider(
                            value: Binding(
                                get: { playerVM.audioService.currentTime },
                                set: { playerVM.audioService.seek(to: $0) }
                            ),
                            in: 0...max(playerVM.audioService.duration, 1)
                        )

                        HStack {
                            Text(Book.formatScrubberTime(playerVM.audioService.currentTime))
                                .font(.system(size: 11, design: .monospaced))
                                .foregroundColor(.secondary)
                            Spacer()
                            Text("-" + Book.formatScrubberTime(max(playerVM.audioService.duration - playerVM.audioService.currentTime, 0)))
                                .font(.system(size: 11, design: .monospaced))
                                .foregroundColor(.secondary)
                        }
                    }
                    .frame(maxWidth: 500)

                    // Transport controls
                    HStack(spacing: 28) {
                        // Speed button
                        Menu {
                            ForEach(AudioPlayerService.speeds, id: \.self) { speed in
                                Button {
                                    playerVM.audioService.setSpeed(speed)
                                } label: {
                                    HStack {
                                        Text(speed == Float(Int(speed)) ? "\(Int(speed))x" : String(format: "%.2gx", speed))
                                        if playerVM.audioService.playbackRate == speed {
                                            Image(systemName: "checkmark")
                                        }
                                    }
                                }
                            }
                        } label: {
                            Text(playerVM.speedLabel)
                                .font(.caption)
                                .fontWeight(.medium)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(.quaternary)
                                .cornerRadius(6)
                        }
                        .menuStyle(.borderlessButton)
                        .fixedSize()

                        Button { playerVM.audioService.skipBackward() } label: {
                            Image(systemName: "gobackward.30")
                                .font(.title)
                        }
                        .buttonStyle(.plain)

                        Button { playerVM.audioService.togglePlayPause() } label: {
                            Image(systemName: playerVM.audioService.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                                .font(.system(size: 52))
                        }
                        .buttonStyle(.plain)

                        Button { playerVM.audioService.skipForward() } label: {
                            Image(systemName: "goforward.30")
                                .font(.title)
                        }
                        .buttonStyle(.plain)

                        // Sleep timer
                        Menu {
                            if playerVM.sleepTimerActive {
                                Button {
                                    playerVM.cancelSleepTimer()
                                } label: {
                                    Label("Cancel Timer", systemImage: "xmark")
                                }
                            } else {
                                ForEach(PlayerViewModel.sleepTimerPresets, id: \.self) { minutes in
                                    Button {
                                        playerVM.startSleepTimer(minutes: minutes)
                                    } label: {
                                        Text("\(minutes) minutes")
                                    }
                                }
                                if !playerVM.chapters.isEmpty {
                                    Divider()
                                    Button {
                                        playerVM.startSleepTimerEndOfChapter()
                                    } label: {
                                        Text("End of Chapter")
                                    }
                                }
                            }
                        } label: {
                            if playerVM.sleepTimerActive {
                                if playerVM.sleepTimerEndOfChapter {
                                    Text("EoC")
                                        .font(.caption)
                                        .fontWeight(.medium)
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(Color.accentColor.opacity(0.2))
                                        .cornerRadius(6)
                                } else {
                                    Text(playerVM.sleepTimerRemainingFormatted)
                                        .font(.caption)
                                        .fontWeight(.medium)
                                        .monospacedDigit()
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(Color.accentColor.opacity(0.2))
                                        .cornerRadius(6)
                                }
                            } else {
                                Image(systemName: "moon.zzz")
                                    .font(.title3)
                                    .foregroundColor(.secondary)
                            }
                        }
                        .menuStyle(.borderlessButton)
                        .fixedSize()
                        .help(playerVM.sleepTimerActive ? "Sleep timer active" : "Sleep Timer")

                        // Bookmark button
                        Button {
                            withAnimation(AppAnimation.expand) {
                                playerVM.showBookmarkList.toggle()
                            }
                        } label: {
                            Image(systemName: playerVM.showBookmarkList ? "bookmark.fill" : "bookmark")
                                .font(.title3)
                                .foregroundColor(playerVM.showBookmarkList ? .accentColor : .secondary)
                        }
                        .buttonStyle(.plain)
                        .help("Bookmarks (\(playerVM.bookmarks.count))")

                        // Chapter toggle (only if chapters exist)
                        if !playerVM.chapters.isEmpty {
                            Button {
                                withAnimation(AppAnimation.expand) {
                                    playerVM.showChapterList.toggle()
                                }
                            } label: {
                                Image(systemName: "list.bullet")
                                    .font(.title3)
                                    .foregroundColor(playerVM.showChapterList ? .accentColor : .secondary)
                            }
                            .buttonStyle(.plain)
                            .help("Chapters")
                        }
                    }

                    // Bookmark list (expandable)
                    if playerVM.showBookmarkList {
                        BookmarkListView()
                            .frame(maxWidth: 500)
                            .transition(.expandSection)
                    }

                    // Chapter list (expandable)
                    if playerVM.showChapterList && !playerVM.chapters.isEmpty {
                        ChapterListView()
                            .frame(maxWidth: 500)
                            .transition(.expandSection)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 30)
                .padding(.bottom, 20)
            }
        }
        .sheet(isPresented: $playerVM.showAddBookmark) {
            AddBookmarkSheet()
                .environmentObject(playerVM)
        }
    }
}
