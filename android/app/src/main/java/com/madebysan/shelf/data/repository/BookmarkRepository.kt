package com.madebysan.shelf.data.repository

import com.madebysan.shelf.data.local.dao.BookmarkDao
import com.madebysan.shelf.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao
) {
    fun getBookmarksByBook(bookId: Long): Flow<List<BookmarkEntity>> =
        bookmarkDao.getBookmarksByBook(bookId)

    suspend fun addBookmark(bookId: Long, timestamp: Long, name: String, note: String?): Long {
        return bookmarkDao.insert(
            BookmarkEntity(
                bookId = bookId,
                timestamp = timestamp,
                name = name,
                note = note
            )
        )
    }

    suspend fun deleteBookmark(id: Long) = bookmarkDao.delete(id)
}
