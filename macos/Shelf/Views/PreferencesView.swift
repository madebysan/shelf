import SwiftUI

/// Preferences/Settings window
struct PreferencesView: View {
    @EnvironmentObject var libraryVM: LibraryViewModel

    @State private var showManageLibraries = false

    var body: some View {
        Form {
            Section("Library") {
                HStack {
                    VStack(alignment: .leading) {
                        if let library = libraryVM.activeLibrary {
                            Text(library.displayName)
                                .font(.headline)
                            if let path = library.folderPath {
                                Text(path)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .lineLimit(1)
                                    .truncationMode(.middle)
                            }
                            Text("\(libraryVM.books.count) book\(libraryVM.books.count == 1 ? "" : "s")")
                                .font(.caption)
                                .foregroundStyle(.tertiary)
                        } else {
                            Text("No Library Selected")
                                .font(.headline)
                            Text("Add a library to get started.")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }

                    Spacer()

                    Button("Manage Libraries...") {
                        showManageLibraries = true
                    }
                }

                HStack {
                    Button("Refresh Library") {
                        Task { await libraryVM.scanLibrary() }
                    }
                    .disabled(libraryVM.isScanning || libraryVM.isLoadingMetadata || libraryVM.activeLibrary == nil)

                    Button("Re-scan All Metadata") {
                        Task { await libraryVM.forceRefreshLibrary() }
                    }
                    .disabled(libraryVM.isScanning || libraryVM.isLoadingMetadata || libraryVM.activeLibrary == nil)
                }

                Text("Refresh checks for new or removed files. Re-scan re-extracts all metadata (covers, titles, etc.).")
                    .font(.caption)
                    .foregroundColor(.secondary)

                HStack {
                    Button("Look Up Missing Covers") {
                        Task { await libraryVM.lookUpMissingCovers() }
                    }
                    .disabled(libraryVM.isScanning || libraryVM.isLoadingMetadata || libraryVM.isLookingUpCovers || libraryVM.activeLibrary == nil)
                }

                Text("Searches Open Library for cover art using book titles. Does not download audio files.")
                    .font(.caption)
                    .foregroundColor(.secondary)

                if libraryVM.isScanning {
                    ProgressView()
                        .controlSize(.small)
                }

                if libraryVM.isLoadingMetadata {
                    VStack(alignment: .leading, spacing: 4) {
                        ProgressView(value: Double(libraryVM.metadataProgress), total: Double(max(libraryVM.metadataTotal, 1)))
                            .controlSize(.small)
                        Text("Extracting metadata: \(libraryVM.metadataProgress) / \(libraryVM.metadataTotal)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }

                if libraryVM.isLookingUpCovers {
                    VStack(alignment: .leading, spacing: 4) {
                        ProgressView(value: Double(libraryVM.coverLookupProgress), total: Double(max(libraryVM.coverLookupTotal, 1)))
                            .controlSize(.small)
                        Text("Finding covers: \(libraryVM.coverLookupProgress) / \(libraryVM.coverLookupTotal)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }

                if let result = libraryVM.scanResult {
                    Text(result.summary)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }

            Section("Backup") {
                HStack {
                    VStack(alignment: .leading) {
                        Text("Export Progress")
                            .font(.headline)
                        Text("Save your playback positions and bookmarks to a JSON file.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                    Button("Export...") {
                        libraryVM.exportProgress()
                    }
                }

                HStack {
                    VStack(alignment: .leading) {
                        Text("Import Progress")
                            .font(.headline)
                        Text("Restore playback positions and bookmarks from a backup file.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                    Button("Import...") {
                        libraryVM.importProgress()
                    }
                }
            }

            Section("About") {
                Text("Shelf v1.0")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text("A native audiobook player for macOS.")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text("Made by [santiagoalonso.com](https://santiagoalonso.com)")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .formStyle(.grouped)
        .frame(width: 450, height: 500)
        .sheet(isPresented: $showManageLibraries) {
            ManageLibrariesView()
                .environmentObject(libraryVM)
        }
    }
}
