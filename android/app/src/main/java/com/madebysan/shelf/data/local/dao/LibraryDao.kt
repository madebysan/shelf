package com.madebysan.shelf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.madebysan.shelf.data.local.entity.LibraryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Query("SELECT * FROM libraries ORDER BY name ASC")
    fun getAllLibraries(): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM libraries WHERE id = :id")
    suspend fun getLibraryById(id: Long): LibraryEntity?

    @Query("SELECT * FROM libraries WHERE driveFolderId = :folderId")
    suspend fun getLibraryByFolderId(folderId: String): LibraryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(library: LibraryEntity): Long

    @Update
    suspend fun update(library: LibraryEntity)

    @Query("DELETE FROM libraries WHERE id = :id")
    suspend fun delete(id: Long)
}
