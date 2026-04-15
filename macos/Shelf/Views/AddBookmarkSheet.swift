import SwiftUI

/// Modal sheet for adding a new bookmark at the current playback position
struct AddBookmarkSheet: View {
    @EnvironmentObject var playerVM: PlayerViewModel
    @Environment(\.dismiss) var dismiss

    @State private var name: String = ""
    @State private var note: String = ""

    var body: some View {
        VStack(spacing: 20) {
            // Header
            Text("Add Bookmark")
                .font(.headline)

            // Current timestamp display
            HStack {
                Image(systemName: "bookmark.fill")
                    .foregroundColor(.accentColor)
                Text("at \(Book.formatScrubberTime(playerVM.audioService.currentTime))")
                    .font(.system(.body, design: .monospaced))
                    .foregroundColor(.secondary)
            }

            // Name field
            VStack(alignment: .leading, spacing: 4) {
                Text("Name")
                    .font(.caption)
                    .foregroundColor(.secondary)
                TextField("e.g., Favorite quote, Important scene", text: $name)
                    .textFieldStyle(.roundedBorder)
            }

            // Note field (optional)
            VStack(alignment: .leading, spacing: 4) {
                Text("Note (optional)")
                    .font(.caption)
                    .foregroundColor(.secondary)
                TextField("Add a note...", text: $note)
                    .textFieldStyle(.roundedBorder)
            }

            // Buttons
            HStack {
                Button("Cancel") {
                    dismiss()
                }
                .keyboardShortcut(.cancelAction)

                Spacer()

                Button("Save") {
                    playerVM.addBookmark(name: name, note: note.isEmpty ? nil : note)
                    dismiss()
                }
                .keyboardShortcut(.defaultAction)
                .disabled(name.isEmpty)
            }
        }
        .padding(24)
        .frame(width: 360)
    }
}
