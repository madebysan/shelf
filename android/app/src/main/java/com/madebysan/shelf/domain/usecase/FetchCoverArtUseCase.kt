package com.madebysan.shelf.domain.usecase

import com.madebysan.shelf.data.repository.BookRepository
import com.madebysan.shelf.service.cover.CoverArtService
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FetchCoverArtUseCase @Inject constructor(
    private val coverArtService: CoverArtService,
    private val bookRepository: BookRepository
) {
    /**
     * Looks up cover art for all books in a library that don't have one yet.
     */
    suspend fun execute(libraryId: Long) {
        val books = bookRepository.getBooksByLibrary(libraryId).first()
        for (book in books) {
            if (book.coverArtUrl != null) continue

            val url = coverArtService.findCoverArt(book.title, book.author)
            if (url != null) {
                bookRepository.updateCoverArt(book.id, null, url)
            }
        }
    }
}
