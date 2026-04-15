import CoreData

/// Manages the Core Data stack for the audiobook library.
/// Uses NSPersistentCloudKitContainer to automatically sync all entities to iCloud.
struct PersistenceController {
    static let shared = PersistenceController()
    static var loadError: String?

    let container: NSPersistentCloudKitContainer

    init(inMemory: Bool = false) {
        container = NSPersistentCloudKitContainer(name: "AudiobookModel")

        if inMemory {
            container.persistentStoreDescriptions.first?.url = URL(fileURLWithPath: "/dev/null")
        }

        // Configure the persistent store for CloudKit sync
        if let description = container.persistentStoreDescriptions.first {
            // Enable lightweight migration
            description.setOption(true as NSNumber, forKey: NSMigratePersistentStoresAutomaticallyOption)
            description.setOption(true as NSNumber, forKey: NSInferMappingModelAutomaticallyOption)

            // Enable persistent history tracking (required for CloudKit sync)
            description.setOption(true as NSNumber, forKey: NSPersistentHistoryTrackingKey)
            description.setOption(true as NSNumber, forKey: NSPersistentStoreRemoteChangeNotificationPostOptionKey)

            // Set the CloudKit container identifier
            if !inMemory {
                description.cloudKitContainerOptions = NSPersistentCloudKitContainerOptions(
                    containerIdentifier: "iCloud.com.madebysan.ShelfIOS"
                )
            }
        }

        container.loadPersistentStores { _, error in
            if let error = error as NSError? {
                let message = "Core Data store failed to load: \(error), \(error.userInfo)"
                print(message)
                PersistenceController.loadError = message
            }
        }

        container.viewContext.automaticallyMergesChangesFromParent = true
        container.viewContext.mergePolicy = NSMergeByPropertyObjectTrumpMergePolicy
    }

    /// Save the view context if there are changes
    func save() {
        let context = container.viewContext
        guard context.hasChanges else { return }
        do {
            try context.save()
        } catch {
            print("Core Data save error: \(error.localizedDescription)")
        }
    }
}
