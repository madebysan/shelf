package com.madebysan.shelf.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "libraries")
data class LibraryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val driveFolderId: String,
    val driveFolderName: String,
    val createdDate: Long = System.currentTimeMillis(),
    val lastSyncDate: Long? = null
)
