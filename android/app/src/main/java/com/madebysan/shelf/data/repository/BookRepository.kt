package com.madebysan.shelf.data.repository

import com.madebysan.shelf.data.local.dao.BookDao
import com.madebysan.shelf.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepository @Inject constructor(
    private val bookDao: BookDao
) {
    fun getBooksByLibrary(libraryId: Long): Flow<List<BookEntity>> =
        bookDao.getBooksByLibrary(libraryId)

    fun getAllBooksByLibrary(libraryId: Long): Flow<List<BookEntity>> =
        bookDao.getAllBooksByLibrary(libraryId)

    suspend fun getAllBooksByLibraryOnce(libraryId: Long): List<BookEntity> =
        bookDao.getAllBooksByLibraryOnce(libraryId)

    suspend fun getBookById(id: Long): BookEntity? = bookDao.getBookById(id)

    suspend fun getBookByDriveFileId(driveFileId: String): BookEntity? =
        bookDao.getBookByDriveFileId(driveFileId)

    suspend fun insertBooks(books: List<BookEntity>): List<Long> =
        bookDao.insertAll(books)

    suspend fun updatePlaybackPosition(bookId: Long, position: Long) {
        bookDao.updatePlaybackPosition(bookId, position, System.currentTimeMillis(), System.currentTimeMillis())
    }

    suspend fun updateStarred(bookId: Long, starred: Boolean) =
        bookDao.updateStarred(bookId, starred)

    suspend fun updateRating(bookId: Long, rating: Int) =
        bookDao.updateRating(bookId, rating)

    suspend fun updateHidden(bookId: Long, hidden: Boolean) =
        bookDao.updateHidden(bookId, hidden)

    suspend fun updateCompleted(bookId: Long, completed: Boolean) =
        bookDao.updateCompleted(bookId, completed)

    suspend fun updateCoverArt(bookId: Long, data: ByteArray?, url: String?) =
        bookDao.updateCoverArt(bookId, data, url)

    suspend fun updateBook(book: BookEntity) = bookDao.update(book)
}
