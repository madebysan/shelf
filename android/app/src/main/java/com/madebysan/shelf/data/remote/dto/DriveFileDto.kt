package com.madebysan.shelf.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class DriveFileListResponse(
    val files: List<DriveFileDto> = emptyList(),
    val nextPageToken: String? = null
)

@Serializable
data class DriveFileDto(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: String? = null,
    val modifiedTime: String? = null
) {
    val isFolder: Boolean get() = mimeType == "application/vnd.google-apps.folder"
}
