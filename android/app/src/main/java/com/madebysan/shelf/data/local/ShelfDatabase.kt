package com.madebysan.shelf.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.madebysan.shelf.data.local.dao.BookDao
import com.madebysan.shelf.data.local.dao.BookmarkDao
import com.madebysan.shelf.data.local.dao.LibraryDao
import com.madebysan.shelf.data.local.entity.BookEntity
import com.madebysan.shelf.data.local.entity.BookmarkEntity
import com.madebysan.shelf.data.local.entity.LibraryEntity

@Database(
    entities = [BookEntity::class, LibraryEntity::class, BookmarkEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ShelfDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun libraryDao(): LibraryDao
    abstract fun bookmarkDao(): BookmarkDao
}
