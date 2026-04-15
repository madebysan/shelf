package com.madebysan.shelf.data.repository

import com.madebysan.shelf.data.local.dao.LibraryDao
import com.madebysan.shelf.data.local.entity.LibraryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val libraryDao: LibraryDao
) {
    fun getAllLibraries(): Flow<List<LibraryEntity>> = libraryDao.getAllLibraries()

    suspend fun getLibraryById(id: Long): LibraryEntity? = libraryDao.getLibraryById(id)

    suspend fun getOrCreateLibrary(driveFolderId: String, folderName: String): LibraryEntity {
        val existing = libraryDao.getLibraryByFolderId(driveFolderId)
        if (existing != null) return existing

        val entity = LibraryEntity(
            name = folderName,
            driveFolderId = driveFolderId,
            driveFolderName = folderName
        )
        val id = libraryDao.insert(entity)
        return entity.copy(id = id)
    }

    suspend fun updateLastSync(libraryId: Long) {
        val library = libraryDao.getLibraryById(libraryId) ?: return
        libraryDao.update(library.copy(lastSyncDate = System.currentTimeMillis()))
    }
}
