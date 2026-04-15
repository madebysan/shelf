import SwiftUI

/// Sidebar navigation with collapsible groups — inspired by Things app.
/// Includes a library dropdown at the top for switching between multiple libraries.
struct SidebarView: View {
    @EnvironmentObject var libraryVM: LibraryViewModel

    // Persist expansion state across launches
    @AppStorage("sidebar_smartCollectionsExpanded") private var smartCollectionsExpanded = false
    @AppStorage("sidebar_authorsExpanded") private var authorsExpanded = false
    @AppStorage("sidebar_genresExpanded") private var genresExpanded = false
    @AppStorage("sidebar_yearsExpanded") private var yearsExpanded = false

    @State private var showManageLibraries = false

    /// Smart collections that have at least one matching book (uses pre-computed counts)
    private var activeSmartCollections: [(collection: LibraryViewModel.SmartCollection, count: Int)] {
        LibraryViewModel.SmartCollection.allCases.compactMap { collection in
            let count = libraryVM.smartCollectionCounts[collection] ?? 0
            return count > 0 ? (collection, count) : nil
        }
    }

    var body: some View {
        List(selection: $libraryVM.selectedCategory) {
            // Library dropdown — Eagle-style switcher at the top
            Section {
                libraryDropdown
            }

            // Library — always visible
            Section {
                sidebarRow("All Books", icon: "books.vertical", count: libraryVM.books.count - libraryVM.hiddenCount)
                    .tag(LibraryViewModel.SidebarCategory.allBooks)

                sidebarRow("In Progress", icon: "book", count: libraryVM.inProgressCount)
                    .tag(LibraryViewModel.SidebarCategory.inProgress)

                sidebarRow("Completed", icon: "checkmark.circle", count: libraryVM.completedCount)
                    .tag(LibraryViewModel.SidebarCategory.completed)

                if libraryVM.hiddenCount > 0 {
                    sidebarRow("Hidden", icon: "eye.slash", count: libraryVM.hiddenCount)
                        .tag(LibraryViewModel.SidebarCategory.hidden)
                }
            } header: {
                Text("Library")
            }

            // Smart Collections — only shown if any have matching books
            if !activeSmartCollections.isEmpty {
                DisclosureGroup(isExpanded: $smartCollectionsExpanded) {
                    ForEach(activeSmartCollections, id: \.collection) { item in
                        sidebarRow(item.collection.rawValue, icon: item.collection.icon, count: item.count)
                            .tag(LibraryViewModel.SidebarCategory.smartCollection(item.collection))
                    }
                } label: {
                    sectionHeader("Smart Collections", count: activeSmartCollections.count)
                }
            }

            // Authors
            if !libraryVM.authors.isEmpty {
                DisclosureGroup(isExpanded: $authorsExpanded) {
                    ForEach(libraryVM.authors, id: \.self) { author in
                        sidebarRow(author, icon: "person", count: libraryVM.authorCounts[author] ?? 0)
                            .tag(LibraryViewModel.SidebarCategory.author(author))
                    }
                } label: {
                    sectionHeader("Authors", count: libraryVM.authors.count)
                }

            }

            // Genres
            if !libraryVM.genres.isEmpty {
                DisclosureGroup(isExpanded: $genresExpanded) {
                    ForEach(libraryVM.genres, id: \.self) { genre in
                        sidebarRow(genre, icon: "tag", count: libraryVM.genreCounts[genre] ?? 0)
                            .tag(LibraryViewModel.SidebarCategory.genre(genre))
                    }
                } label: {
                    sectionHeader("Genres", count: libraryVM.genres.count)
                }
            }

            // Years
            if !libraryVM.years.isEmpty {
                DisclosureGroup(isExpanded: $yearsExpanded) {
                    ForEach(libraryVM.years, id: \.self) { year in
                        sidebarRow(String(year), icon: "calendar", count: libraryVM.yearCounts[year] ?? 0)
                            .tag(LibraryViewModel.SidebarCategory.year(year))
                    }
                } label: {
                    sectionHeader("Years", count: libraryVM.years.count)
                }
            }
        }
        .listStyle(.sidebar)
        .frame(minWidth: 200)
        .toolbar {
            ToolbarItem {
                Button {
                    Task { await libraryVM.scanLibrary() }
                } label: {
                    Label("Refresh", systemImage: "arrow.clockwise")
                }
                .disabled(libraryVM.isScanning || libraryVM.activeLibrary == nil)
                .help("Refresh Library")
            }
        }
        .sheet(isPresented: $showManageLibraries) {
            ManageLibrariesView()
                .environmentObject(libraryVM)
        }
    }

    // MARK: - Library Dropdown

    /// Eagle-style dropdown showing current library with options to switch/add/manage
    private var libraryDropdown: some View {
        Menu {
            // List each library as a switchable option
            ForEach(libraryVM.libraries, id: \.objectID) { library in
                Button {
                    libraryVM.switchToLibrary(library)
                } label: {
                    HStack {
                        if library == libraryVM.activeLibrary {
                            Image(systemName: "checkmark")
                        }
                        VStack(alignment: .leading) {
                            Text(library.displayName)
                            Text(library.truncatedPath)
                                .font(.caption)
                        }
                    }
                }
            }

            Divider()

            Button {
                libraryVM.addLibrary()
            } label: {
                Label("Add Library...", systemImage: "plus")
            }

            Divider()

            Button {
                showManageLibraries = true
            } label: {
                Label("Manage Libraries...", systemImage: "gearshape")
            }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "books.vertical.fill")
                    .font(.title3)

                VStack(alignment: .leading, spacing: 1) {
                    Text(libraryVM.activeLibrary?.displayName ?? "No Library")
                        .font(.headline)
                        .lineLimit(1)

                    if let path = libraryVM.activeLibrary?.folderPath {
                        Text(abbreviatePath(path))
                            .font(.caption)
                            .opacity(0.6)
                            .lineLimit(1)
                            .truncationMode(.middle)
                    }
                }

                Spacer()

                Image(systemName: "chevron.up.chevron.down")
                    .font(.caption)
                    .opacity(0.4)
            }
            .padding(.vertical, 4)
            .contentShape(Rectangle())
        }
        .menuStyle(.borderlessButton)
        .tint(.primary)
    }

    /// Shortens a path for display (replaces home dir with ~)
    private func abbreviatePath(_ path: String) -> String {
        let home = FileManager.default.homeDirectoryForCurrentUser.path
        if path.hasPrefix(home) {
            return "~" + path.dropFirst(home.count)
        }
        return path
    }

    // MARK: - Components

    /// A single sidebar row with icon, label, and trailing count
    private func sidebarRow(_ title: String, icon: String, count: Int) -> some View {
        HStack {
            Label(title, systemImage: icon)
                .lineLimit(1)

            Spacer()

            if count > 0 {
                Text("\(count)")
                    .font(.caption2)
                    .fontWeight(.medium)
                    .foregroundStyle(.tertiary)
                    .padding(.trailing, 2)
            }
        }
    }

    /// Disclosure group header styled like a section title
    private func sectionHeader(_ title: String, count: Int) -> some View {
        HStack {
            Text(title)
                .font(.subheadline)
                .fontWeight(.semibold)
                .foregroundColor(.secondary)

            Spacer()

            Text("\(count)")
                .font(.caption2)
                .foregroundStyle(.tertiary)
                .padding(.trailing, 2)
        }
        .contentShape(Rectangle())
    }

}
