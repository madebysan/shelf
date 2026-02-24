import SwiftUI

/// Card size variants for grid views
enum BookCardSize {
    case regular  // 160x160 cover
    case large    // 240x240 cover

    var coverSize: CGFloat {
        switch self {
        case .regular: return 160
        case .large: return 240
        }
    }

    var cardWidth: CGFloat { coverSize }

    var titleFont: Font {
        switch self {
        case .regular: return .caption
        case .large: return .body
        }
    }

    var subtitleFont: Font {
        switch self {
        case .regular: return .caption2
        case .large: return .caption
        }
    }

    var playIconSize: CGFloat {
        switch self {
        case .regular: return 44
        case .large: return 56
        }
    }
}

/// A single book card in the library grid: cover art, title, author, duration, progress bar
struct BookCardView: View {
    let book: Book
    let size: BookCardSize
    let onTap: () -> Void

    @EnvironmentObject var libraryVM: LibraryViewModel
    @EnvironmentObject var playerVM: PlayerViewModel
    @State private var isHovering = false

    init(book: Book, size: BookCardSize = .regular, onTap: @escaping () -> Void) {
        self.book = book
        self.size = size
        self.onTap = onTap
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            // Cover art (1:1 aspect ratio)
            Button(action: onTap) {
                ZStack(alignment: .bottomTrailing) {
                    Image(nsImage: book.coverImage)
                        .resizable()
                        .aspectRatio(1, contentMode: .fill)
                        .frame(width: size.coverSize, height: size.coverSize)
                        .clipped()
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .shadow(color: .black.opacity(0.15), radius: 4, y: 2)

                    // Download progress overlay — shown while this book is downloading
                    if playerVM.downloadingBookID == book.objectID {
                        ZStack {
                            RoundedRectangle(cornerRadius: 8)
                                .fill(.black.opacity(0.45))

                            VStack(spacing: 6) {
                                ProgressView(value: playerVM.downloadProgress)
                                    .progressViewStyle(.circular)
                                    .scaleEffect(size == .large ? 1.5 : 1.2)
                                    .tint(.white)

                                Text("\(Int(playerVM.downloadProgress * 100))%")
                                    .font(size == .large ? .caption : .caption2)
                                    .fontWeight(.medium)
                                    .foregroundColor(.white)
                            }
                        }
                    } else {
                        // Play overlay — always in tree, visibility via opacity
                        ZStack {
                            RoundedRectangle(cornerRadius: 8)
                                .fill(.black.opacity(0.3))

                            Image(systemName: "play.circle.fill")
                                .font(.system(size: size.playIconSize))
                                .foregroundColor(.white)
                                .shadow(radius: 4)
                        }
                        .opacity(isHovering ? 1 : 0)
                    }

                    // Progress badge
                    if book.progress > 0 && !book.isCompleted {
                        Text(book.progressPercentage)
                            .font(size == .large ? .caption : .caption2)
                            .fontWeight(.medium)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(.ultraThinMaterial)
                            .cornerRadius(4)
                            .padding(6)
                    }

                    // Completed badge
                    if book.isCompleted {
                        Image(systemName: "checkmark.circle.fill")
                            .font(size == .large ? .title2 : .title3)
                            .foregroundColor(.green)
                            .shadow(radius: 2)
                            .padding(6)
                    }

                    // Cloud-only badge (top-leading corner)
                    if book.isCloudOnly {
                        VStack {
                            HStack {
                                Image(systemName: "icloud.and.arrow.down")
                                    .font(size == .large ? .caption : .caption2)
                                    .foregroundColor(.white)
                                    .padding(4)
                                    .background(.ultraThinMaterial)
                                    .cornerRadius(4)
                                    .padding(6)
                                Spacer()
                            }
                            Spacer()
                        }
                    }
                }
                .frame(width: size.coverSize, height: size.coverSize)
            }
            .buttonStyle(.plain)
            .onHover { hovering in
                withAnimation(AppAnimation.quickToggle) {
                    isHovering = hovering
                }
            }

            // Progress bar (thin line under cover) — always reserves space to keep cards aligned
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Rectangle()
                        .fill(Color.secondary.opacity(0.2))
                        .frame(height: 3)
                        .cornerRadius(1.5)

                    Rectangle()
                        .fill(Color.accentColor)
                        .frame(width: geo.size.width * book.progress, height: 3)
                        .cornerRadius(1.5)
                }
            }
            .frame(height: 3)
            .opacity(book.progress > 0 && !book.isCompleted ? 1 : 0)

            // Title
            Text(book.displayTitle)
                .font(size.titleFont)
                .fontWeight(.medium)
                .lineLimit(2)
                .multilineTextAlignment(.leading)

            // Author
            Text(book.displayAuthor)
                .font(size.subtitleFont)
                .foregroundColor(.secondary)
                .lineLimit(1)

            // Rating stars
            if book.rating > 0 {
                HStack(spacing: 2) {
                    ForEach(1...5, id: \.self) { star in
                        Image(systemName: star <= Int(book.rating) ? "star.fill" : "star")
                            .font(.system(size: size == .large ? 10 : 8))
                            .foregroundColor(star <= Int(book.rating) ? .orange : .secondary.opacity(0.3))
                    }
                }
            }

            // Duration
            if book.duration > 0 {
                Text(book.formattedDuration)
                    .font(size.subtitleFont)
                    .foregroundColor(.secondary)
            }
        }
        .frame(width: size.cardWidth, alignment: .topLeading)
        .frame(maxHeight: .infinity, alignment: .top)
        .contentShape(Rectangle())
        .contextMenu { bookContextMenu }
    }

    // MARK: - Context Menu (shared with BookListRow)

    @ViewBuilder
    var bookContextMenu: some View {
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

        Divider()

        Menu("Book Info") {
            Text("Author: \(book.displayAuthor)")
            Text("Genre: \(book.displayGenre)")
            if book.year > 0 {
                Text("Year: \(book.year)")
            }
            Text("Duration: \(book.duration > 0 ? book.formattedDuration : "Unknown")")
            if book.hasChapters {
                Text("Has chapters")
            }
        }
    }
}
