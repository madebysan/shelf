package com.madebysan.shelf.di

import android.content.Context
import androidx.room.Room
import com.madebysan.shelf.data.local.ShelfDatabase
import com.madebysan.shelf.data.local.dao.BookDao
import com.madebysan.shelf.data.local.dao.BookmarkDao
import com.madebysan.shelf.data.local.dao.LibraryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ShelfDatabase {
        return Room.databaseBuilder(
            context,
            ShelfDatabase::class.java,
            "shelf.db"
        ).build()
    }

    @Provides
    fun provideBookDao(db: ShelfDatabase): BookDao = db.bookDao()

    @Provides
    fun provideLibraryDao(db: ShelfDatabase): LibraryDao = db.libraryDao()

    @Provides
    fun provideBookmarkDao(db: ShelfDatabase): BookmarkDao = db.bookmarkDao()
}
