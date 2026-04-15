import Foundation
import CoreData

// Convenience accessors for the Core Data Library entity
extension Library {

    /// Display name — falls back to the folder name if no custom name is set
    var displayName: String {
        if let n = name, !n.isEmpty { return n }
        guard let path = folderPath else { return "Untitled Library" }
        return URL(fileURLWithPath: path).lastPathComponent
    }

    /// Shortened path for display in menus (replaces home dir with ~, shows last 2 components)
    var truncatedPath: String {
        guard let path = folderPath else { return "" }
        let home = FileManager.default.homeDirectoryForCurrentUser.path
        if path.hasPrefix(home) {
            return "~" + path.dropFirst(home.count)
        }
        return path
    }
}
