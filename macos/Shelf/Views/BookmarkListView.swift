import SwiftUI

/// Scrollable list of bookmarks with tap-to-jump and swipe-to-delete
struct BookmarkListView: View {
    @EnvironmentObject var playerVM: PlayerViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text("Bookmarks")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(.secondary)

                Spacer()

                Text("\(playerVM.bookmarks.count)")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }
            .padding(.horizontal, 4)

            if playerVM.bookmarks.isEmpty {
                Text("No bookmarks yet. Tap the bookmark button to add one.")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .padding(.horizontal, 4)
                    .padding(.vertical, 8)
                    .emptyStateAppear()
            } else {
                List {
                    ForEach(playerVM.bookmarks, id: \.id) { bookmark in
                        Button {
                            playerVM.jumpToBookmark(bookmark)
                        } label: {
                            HStack {
                                Image(systemName: "bookmark.fill")
                                    .font(.caption2)
                                    .foregroundColor(.accentColor)
                                    .frame(width: 16)

                                VStack(alignment: .leading, spacing: 1) {
                                    Text(bookmark.displayName)
                                        .font(.caption)
                                        .lineLimit(1)

                                    if let note = bookmark.note, !note.isEmpty {
                                        Text(note)
                                            .font(.caption2)
                                            .foregroundColor(.secondary)
                                            .lineLimit(1)
                                    }
                                }

                                Spacer()

                                Text(bookmark.formattedTimestamp)
                                    .font(.system(size: 10, design: .monospaced))
                                    .foregroundColor(.secondary)
                            }
                            .padding(.vertical, 2)
                        }
                        .buttonStyle(.plain)
                    }
                    .onDelete { indexSet in
                        for index in indexSet {
                            playerVM.deleteBookmark(playerVM.bookmarks[index])
                        }
                    }
                }
                .listStyle(.plain)
                .frame(maxHeight: 200)
            }
        }
    }
}
