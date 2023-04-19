package com.tomer.tomershare.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore

class PathUtils {
    companion object {
        fun Uri.getImagePath(context: Context): String? {
            val proj = arrayOf(MediaStore.Images.Media.DATA)
            val cursor = context.contentResolver.query(this, proj, null, null, null)
            try {
                if (cursor != null) {
                    val ci = cursor.getColumnIndexOrThrow(proj[0])
                    cursor.moveToFirst()
                    val ret = cursor.getString(ci)
                    cursor.close()
                    return ret
                }
            } catch (_: Exception) {
                return null
            }

            return null
        }

        fun Uri.getVideoPath(context: Context): String? {
            val proj = arrayOf(MediaStore.Video.Media.DATA)
            val cursor = context.contentResolver.query(this, proj, null, null, null)
            try {
                if (cursor != null) {
                    val ci = cursor.getColumnIndexOrThrow(proj[0])
                    cursor.moveToFirst()
                    val ret = cursor.getString(ci)
                    cursor.close()
                    return ret
                }
            } catch (_: Exception) {
                return null
            }

            return null
        }

        fun Uri.getFilePath(context: Context): String? {
            val path: String?
            val proj = arrayOf(MediaStore.Files.FileColumns.DATA)
            val cursor = context.contentResolver.query(this, proj, null, null, null)
            if (cursor == null) {
                path = this.path
            } else {
                try {
                    cursor.moveToFirst()
                    val ci = cursor.getColumnIndexOrThrow(proj[0])
                    path = cursor.getString(ci)
                    cursor.close()
                } catch (_: Exception) {
                    return null
                }
            }
            return if (path == null || path.isEmpty()) this.path else path
        }

        fun fileName(uri: Uri): String {
            var res = uri.path
            val cut = res!!.lastIndexOf("/")
            if (cut != -1) {
                res = res.substring(cut + 1)
            }
            return res
        }
    }
}