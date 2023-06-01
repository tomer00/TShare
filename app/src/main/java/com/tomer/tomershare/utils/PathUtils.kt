package com.tomer.tomershare.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.tomer.tomershare.utils.Utils.Companion.fileName

class PathUtils {
    companion object {
        fun Uri.getImagePath(context: Context): Pair<String, String> {
            val proj = arrayOf(MediaStore.Images.Media.DATA, MediaStore.Images.Media.DISPLAY_NAME)
            val cursor = context.contentResolver.query(this, proj, null, null, null)
            try {
                if (cursor != null) {
                    cursor.moveToFirst()
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(proj[0]))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(proj[1]))
                    cursor.close()
                    return Pair(path, name)
                }
            } catch (_: Exception) {
                cursor!!.close()
            }
            return Pair("null", "null")
        }

        fun Uri.getVideoPath(context: Context): Pair<String, String> {
            val proj = arrayOf(MediaStore.Video.Media.DATA, MediaStore.Video.Media.DISPLAY_NAME)
            val cursor = context.contentResolver.query(this, proj, null, null, null)
            try {
                if (cursor != null) {
                    cursor.moveToFirst()
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(proj[0]))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(proj[1]))
                    cursor.close()
                    return Pair(path, name)
                }
            } catch (_: Exception) {
                cursor!!.close()
            }
            return Pair("null", "null")
        }

        fun Uri.getFilePath(context: Context): Pair<String, String> {
            var path = "null"
            val proj = arrayOf(MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.DISPLAY_NAME)
            val cursor = context.contentResolver.query(this, proj, null, null, null)
            if (cursor == null) {
                path = this.path.toString()
            } else {
                try {
                    cursor.moveToFirst()
                    path = cursor.getString(cursor.getColumnIndexOrThrow(proj[0]))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(proj[1]))
                    cursor.close()
                    return Pair(path, name)
                } catch (_: Exception) {
                    cursor.close()
                }
            }
            return if (path == "null" || path.isEmpty()) Pair(this.path.toString(), this.fileName()) else Pair(path, this.fileName())
        }
    }
}