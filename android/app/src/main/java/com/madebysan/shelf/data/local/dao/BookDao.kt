package com.madebysan.shelf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.madebysan.shelf.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books WHERE libraryId = :libraryId AND isHidden = 0 ORDER BY title ASC")
    fun getBooksByLibrary(libraryId: Long): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE libraryId = :libraryId ORDER BY title ASC")
    fun getAllBooksByLibrary(libraryId: Long): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE libraryId = :libraryId ORDER BY title ASC")
    suspend fun getAllBooksByLibraryOnce(libraryId: Long): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE driveFileId = :driveFileId")
    suspend fun getBookByDriveFileId(driveFileId: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(book: BookEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(books: List<BookEntity>): List<Long>

    @Update
    suspend fun update(book: BookEntity)

    @Query("UPDATE books SET playbackPosition = :position, lastPlayedDate = :lastPlayed, modifiedDate = :modified WHERE id = :bookId")
    suspend fun updatePlaybackPosition(bookId: Long, position: Long, lastPlayed: Long, modified: Long)

    @Query("UPDATE books SET isCompleted = :completed, modifiedDate = :modified WHERE id = :bookId")
    suspend fun updateCompleted(bookId: Long, completed: Boolean, modified: Long = System.currentTimeMillis())

    @Query("UPDATE books SET isStarred = :starred, modifiedDate = :modified WHERE id = :bookId")
    suspend fun updateStarred(bookId: Long, starred: Boolean, modified: Long = System.currentTimeMillis())

    @Query("UPDATE books SET isHidden = :hidden, modifiedDate = :modified WHERE id = :bookId")
    suspend fun updateHidden(bookId: Long, hidden: Boolean, modified: Long = System.currentTimeMillis())

    @Query("UPDATE books SET rating = :rating, modifiedDate = :modified WHERE id = :bookId")
    suspend fun updateRating(bookId: Long, rating: Int, modified: Long = System.currentTimeMillis())

    @Query("UPDATE books SET coverArtData = :data, coverArtUrl = :url, modifiedDate = :modified WHERE id = :bookId")
    suspend fun updateCoverArt(bookId: Long, data: ByteArray?, url: String?, modified: Long = System.currentTimeMillis())

    @Query("UPDATE books SET isDownloaded = :downloaded, modifiedDate = :modified WHERE id = :bookId")
    suspend fun updateDownloaded(bookId: Long, downloaded: Boolean, modified: Long = System.currentTimeMillis())

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM books WHERE libraryId = :libraryId")
    suspend fun getBookCount(libraryId: Long): Int
}
