package com.madebysan.shelf.data.remote

import com.madebysan.shelf.data.remote.dto.DriveFileListResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface GoogleDriveApi {

    @GET("drive/v3/files")
    suspend fun listFiles(
        @Query("q") query: String,
        @Query("fields") fields: String = "nextPageToken,files(id,name,mimeType,size,modifiedTime)",
        @Query("pageSize") pageSize: Int = 1000,
        @Query("pageToken") pageToken: String? = null,
        @Query("orderBy") orderBy: String = "name"
    ): DriveFileListResponse
}
