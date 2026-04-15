import SwiftUI

/// Sheet for managing all libraries: rename, relink, remove, add
struct ManageLibrariesView: View {
    @EnvironmentObject var libraryVM: LibraryViewModel
    @Environment(\.dismiss) private var dismiss

    // Tracks which library is being renamed (by objectID)
    @State private var renamingLibrary: NSManagedObjectID?
    @State private var renameText: String = ""

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Text("Manage Libraries")
                    .font(.headline)
                Spacer()
                Button("Done") {
                    dismiss()
                }
                .keyboardShortcut(.defaultAction)
            }
            .padding()

            Divider()

            // Library list
            if libraryVM.libraries.isEmpty {
                VStack(spacing: 12) {
                    Spacer()
                    Image(systemName: "books.vertical")
                        .font(.system(size: 40))
                        .foregroundColor(.secondary)
                    Text("No Libraries")
                        .font(.title3)
                        .foregroundColor(.secondary)
                    Text("Add a library to get started.")
                        .font(.caption)
                        .foregroundStyle(.tertiary)
                    Spacer()
                }
                .frame(maxWidth: .infinity)
            } else {
                List {
                    ForEach(libraryVM.libraries, id: \.objectID) { library in
                        libraryRow(library)
                    }
                }
                .listStyle(.inset)
            }

            Divider()

            // Footer: Add Library button
            HStack {
                Button {
                    libraryVM.addLibrary()
                } label: {
                    Label("Add Library", systemImage: "plus")
                }
                Spacer()
            }
            .padding()
        }
        .frame(width: 500, height: 400)
    }

    // MARK: - Library Row

    private func libraryRow(_ library: Library) -> some View {
        HStack(spacing: 12) {
            // Library icon
            Image(systemName: "folder.fill")
                .font(.title3)
                .foregroundColor(.accentColor)
                .frame(width: 28)

            // Name and path
            VStack(alignment: .leading, spacing: 2) {
                if renamingLibrary == library.objectID {
                    // Inline rename text field
                    TextField("Library Name", text: $renameText, onCommit: {
                        libraryVM.renameLibrary(library, to: renameText)
                        renamingLibrary = nil
                    })
                    .textFieldStyle(.roundedBorder)
                    .font(.body)
                    .onExitCommand {
                        renamingLibrary = nil
                    }
                } else {
                    HStack(spacing: 6) {
                        Text(library.displayName)
                            .font(.body)
                            .lineLimit(1)

                        // Active badge
                        if library == libraryVM.activeLibrary {
                            Text("Active")
                                .font(.caption2)
                                .fontWeight(.medium)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.accentColor.opacity(0.15))
                                .foregroundColor(.accentColor)
                                .clipShape(Capsule())
                        }
                    }
                }

                if let path = library.folderPath {
                    Text(abbreviatePath(path))
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }

                // Book count
                let count = bookCount(for: library)
                Text("\(count) book\(count == 1 ? "" : "s")")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }

            Spacer()

            // Action buttons
            HStack(spacing: 4) {
                // Rename
                Button {
                    renameText = library.displayName
                    renamingLibrary = library.objectID
                } label: {
                    Image(systemName: "pencil")
                }
                .buttonStyle(.borderless)
                .help("Rename Library")

                // Relink (change folder)
                Button {
                    libraryVM.relinkLibrary(library)
                } label: {
                    Image(systemName: "folder")
                }
                .buttonStyle(.borderless)
                .help("Relink Folder")

                // Remove
                Button {
                    confirmRemoval(of: library)
                } label: {
                    Image(systemName: "minus.circle.fill")
                        .foregroundColor(.red)
                }
                .buttonStyle(.borderless)
                .help("Remove Library")
            }
        }
        .padding(.vertical, 4)
    }

    // MARK: - Helpers

    /// Counts books in a library via Core Data
    private func bookCount(for library: Library) -> Int {
        (library.books as? Set<Book>)?.count ?? 0
    }

    /// Shortens a path for display (replaces home dir with ~)
    private func abbreviatePath(_ path: String) -> String {
        let home = FileManager.default.homeDirectoryForCurrentUser.path
        if path.hasPrefix(home) {
            return "~" + path.dropFirst(home.count)
        }
        return path
    }

    /// Shows a confirmation alert before removing a library
    private func confirmRemoval(of library: Library) {
        let alert = NSAlert()
        alert.messageText = "Remove \"\(library.displayName)\"?"
        alert.informativeText = "This removes the library from Shelf. Your audiobook files on disk will not be deleted."
        alert.alertStyle = .warning
        alert.addButton(withTitle: "Remove")
        alert.addButton(withTitle: "Cancel")

        if alert.runModal() == .alertFirstButtonReturn {
            libraryVM.removeLibrary(library)
        }
    }
}
