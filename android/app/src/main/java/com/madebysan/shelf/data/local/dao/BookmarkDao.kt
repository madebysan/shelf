package com.madebysan.shelf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.madebysan.shelf.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY timestamp ASC")
    fun getBookmarksByBook(bookId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY timestamp ASC")
    suspend fun getBookmarksByBookOnce(bookId: Long): List<BookmarkEntity>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)
}
