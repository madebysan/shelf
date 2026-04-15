package com.madebysan.shelf.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["libraryId"]),
        Index(value = ["driveFileId"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = LibraryEntity::class,
            parentColumns = ["id"],
            childColumns = ["libraryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val libraryId: Long,

    // Metadata
    val title: String,
    val author: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val narrator: String? = null,
    val description: String? = null,

    // Rating 0-100
    val rating: Int = 0,

    // Playback
    val duration: Long = 0,           // milliseconds
    val playbackPosition: Long = 0,   // milliseconds
    val playbackSpeed: Float = 1.0f,
    val lastPlayedDate: Long? = null,  // epoch ms

    // Drive info
    val driveFileId: String,
    val filePath: String? = null,      // subfolder path within library
    val fileName: String,
    val fileSize: Long = 0,
    val mimeType: String,

    // Cover art
    val coverArtData: ByteArray? = null,
    val coverArtUrl: String? = null,

    // Flags
    val isStarred: Boolean = false,
    val isHidden: Boolean = false,
    val isCompleted: Boolean = false,
    val isDownloaded: Boolean = false,

    // Dates
    val addedDate: Long = System.currentTimeMillis(),
    val modifiedDate: Long = System.currentTimeMillis()
) {
    val isVideo: Boolean
        get() {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return ext in setOf("mp4", "m4v", "mkv", "avi", "mov", "webm")
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BookEntity) return false
        return id == other.id && driveFileId == other.driveFileId
    }

    override fun hashCode(): Int = id.hashCode()
}
