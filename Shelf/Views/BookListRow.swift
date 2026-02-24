import SwiftUI

/// A single row in the list/table view showing book details in columns
struct BookListRow: View {
    let book: Book
    let onTap: () -> Void
    let isCurrentlyPlaying: Bool

    @EnvironmentObject var libraryVM: LibraryViewModel
    @State private var isHovering = false

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                // Cover art thumbnail
                Image(nsImage: book.coverImage)
                    .resizable()
                    .aspectRatio(1, contentMode: .fill)
                    .frame(width: 40, height: 40)
                    .clipped()
                    .clipShape(RoundedRectangle(cornerRadius: 4))

                // Now playing indicator
                if isCurrentlyPlaying {
                    Image(systemName: "speaker.wave.2.fill")
                        .font(.caption2)
                        .foregroundColor(.accentColor)
                        .frame(width: 14)
                } else {
                    Color.clear.frame(width: 14)
                }

                // Title & author
                VStack(alignment: .leading, spacing: 2) {
                    Text(book.displayTitle)
                        .font(.body)
                        .lineLimit(1)
                        .foregroundColor(isCurrentlyPlaying ? .accentColor : .primary)

                    Text(book.displayAuthor)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
                .frame(minWidth: 200, alignment: .leading)

                Spacer()

                // Genre
                Text(book.displayGenre)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .frame(width: 100, alignment: .leading)
                    .lineLimit(1)

                // Duration
                Text(book.duration > 0 ? book.formattedDuration : "-")
                    .font(.system(.caption, design: .monospaced))
                    .foregroundColor(.secondary)
                    .frame(width: 70, alignment: .trailing)

                // Progress
                HStack(spacing: 6) {
                    if book.isCompleted {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.caption)
                            .foregroundColor(.green)
                        Text("Done")
                            .font(.caption)
                            .foregroundColor(.green)
                    } else if book.progress > 0 {
                        // Mini progress bar
                        ZStack(alignment: .leading) {
                            Rectangle()
                                .fill(Color.secondary.opacity(0.2))
                                .frame(width: 50, height: 4)
                                .cornerRadius(2)

                            Rectangle()
                                .fill(Color.accentColor)
                                .frame(width: 50 * book.progress, height: 4)
                                .cornerRadius(2)
                        }
                        Text(book.progressPercentage)
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    } else {
                        Text("New")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                .frame(width: 90, alignment: .trailing)

                // Rating
                HStack(spacing: 1) {
                    if book.rating > 0 {
                        ForEach(1...5, id: \.self) { star in
                            Image(systemName: star <= Int(book.rating) ? "star.fill" : "star")
                                .font(.system(size: 8))
                                .foregroundColor(star <= Int(book.rating) ? .orange : .secondary.opacity(0.3))
                        }
                    }
                }
                .frame(width: 60, alignment: .trailing)
            }
            .padding(.vertical, 4)
            .padding(.horizontal, 8)
            .contentShape(Rectangle())
            .background(isHovering ? Color.primary.opacity(0.04) : Color.clear)
            .cornerRadius(6)
        }
        .buttonStyle(.plain)
        .onHover { hovering in
            withAnimation(AppAnimation.quickToggle) {
                isHovering = hovering
            }
        }
        .contextMenu {
            // Same context menu as card view
            Button {
                onTap()
            } label: {
                Label(book.isInProgress ? "Resume" : "Play", systemImage: "play.fill")
            }

            Button {
                libraryVM.toggleStarred(book)
            } label: {
                Label(book.isStarred ? "Remove from Starred" : "Add to Starred",
                      systemImage: book.isStarred ? "star.slash" : "star")
            }

            Button {
                libraryVM.toggleHidden(book)
            } label: {
                Label(book.isHidden ? "Unhide" : "Hide",
                      systemImage: book.isHidden ? "eye" : "eye.slash")
            }

            // Rating submenu
            Menu {
                ForEach(0...5, id: \.self) { stars in
                    Button {
                        libraryVM.setRating(book, rating: Int16(stars))
                    } label: {
                        HStack {
                            Text(stars == 0 ? "No Rating" : String(repeating: "\u{2605}", count: stars))
                            if book.rating == Int16(stars) {
                                Image(systemName: "checkmark")
                            }
                        }
                    }
                }
            } label: {
                Label(
                    book.rating > 0 ? "\(String(repeating: "\u{2605}", count: Int(book.rating)))" : "Rate",
                    systemImage: "star"
                )
            }

            Divider()

            if !book.isCompleted {
                Button {
                    libraryVM.markCompleted(book)
                } label: {
                    Label("Mark as Completed", systemImage: "checkmark.circle")
                }
            } else {
                Button {
                    libraryVM.markNotCompleted(book)
                } label: {
                    Label("Mark as Not Completed", systemImage: "arrow.uturn.backward")
                }
            }

            if book.playbackPosition > 0 || book.isCompleted {
                Button {
                    libraryVM.resetProgress(for: book)
                } label: {
                    Label("Reset Progress", systemImage: "arrow.counterclockwise")
                }
            }

            Divider()

            Button {
                libraryVM.refreshMetadata(for: book)
            } label: {
                Label("Refresh Metadata", systemImage: "arrow.trianglehead.2.clockwise")
            }

            Button {
                libraryVM.showInFinder(book)
            } label: {
                Label("Show in Finder", systemImage: "folder")
            }

            Button {
                libraryVM.copyTitle(book)
            } label: {
                Label("Copy Title", systemImage: "doc.on.doc")
            }
        }
    }
}
