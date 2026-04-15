package com.madebysan.shelf.service.drive

import com.madebysan.shelf.data.remote.GoogleDriveApi
import com.madebysan.shelf.data.remote.dto.DriveFileDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveFileManager @Inject constructor(
    private val driveApi: GoogleDriveApi
) {
    suspend fun listFolders(parentId: String = "root"): List<DriveFileDto> {
        val query = "'$parentId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val allFolders = mutableListOf<DriveFileDto>()
        var pageToken: String? = null

        do {
            val response = driveApi.listFiles(
                query = query,
                pageToken = pageToken
            )
            allFolders.addAll(response.files)
            pageToken = response.nextPageToken
        } while (pageToken != null)

        return allFolders
    }

    suspend fun listFiles(parentId: String): List<DriveFileDto> {
        val query = "'$parentId' in parents and trashed = false"
        val allFiles = mutableListOf<DriveFileDto>()
        var pageToken: String? = null

        do {
            val response = driveApi.listFiles(
                query = query,
                pageToken = pageToken
            )
            allFiles.addAll(response.files)
            pageToken = response.nextPageToken
        } while (pageToken != null)

        return allFiles
    }
}
