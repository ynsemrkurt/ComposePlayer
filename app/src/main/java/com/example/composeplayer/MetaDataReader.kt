package com.example.composeplayer

import android.app.Application
import android.net.Uri
import android.provider.MediaStore

data class MetaData(
    val fileName: String,
)

interface MetaDataReader {
    fun getMetaDataFromUri(uri: Uri): MetaData?
}

class MetaDataReaderImpl(
    private val app: Application
) : MetaDataReader {
    override fun getMetaDataFromUri(uri: Uri): MetaData? {
        if (uri.scheme != "content") return null
        val fileName = app.contentResolver
            .query(
                uri,
                arrayOf(MediaStore.Video.VideoColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val columnIndex = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(columnIndex)
            }
        return fileName?.let { fullFileName ->
            MetaData(
                fileName = fullFileName
            )
        }
    }
}