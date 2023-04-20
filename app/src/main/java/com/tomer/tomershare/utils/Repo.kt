package com.tomer.tomershare.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.database.Cursor
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.MediaStore
import com.tomer.tomershare.R
import com.tomer.tomershare.modal.AppModal
import com.tomer.tomershare.modal.GalModal
import java.io.File
import java.util.Locale

class Repo(private val context: Context) {

    fun getAllApps(): List<AppModal> {
        val l = mutableListOf<AppModal>()
        val pkg: PackageManager = context.packageManager
        val apps = pkg.getInstalledPackages(0)
        for (app in apps) {
            if (app.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
                val d: Drawable? = getIcon(pkg, app.packageName)
                if (d != null) {
                    val f = File(app.applicationInfo.sourceDir)
                    l.add(AppModal(pkg.getApplicationLabel(app.applicationInfo).toString(), Utils.humanReadableSize(f.length()), f, d))
                }
            }
        }
        l.sortWith(Comparator.comparing { (name) -> name.lowercase(Locale.ROOT) })
        return l
    }


    fun getAllImagesVideos(): List<GalModal> {
        val cursor: Cursor
        val uri: Uri = MediaStore.Files.getContentUri("external")
        val proj = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )
        val selection = (MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                + " OR " +
                MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
        cursor = context.contentResolver.query(uri, proj, selection, null, MediaStore.Files.FileColumns.DATE_ADDED + " DESC")!!
        val cIndexData: Int = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
        val cIndexType: Int = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val listGal = mutableListOf<GalModal>()
        while (cursor.moveToNext()) {
            val f = File(cursor.getString(cIndexData))
            val isVid = cursor.getString(cIndexType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
            listGal.add(GalModal(f, isVid))
        }
        cursor.close()
        return listGal
    }


    private fun getIcon(pm: PackageManager, pkg: String): Drawable? {
        return try {
            pm.getApplicationIcon(pkg)
        } catch (e: NameNotFoundException) {
            null
        }
    }


    companion object {
        private val map = mutableMapOf<String, Int>().also { mp ->
            mp["1"] = R.drawable.avtar_1
            mp["2"] = R.drawable.avtar_2
            mp["3"] = R.drawable.avtar_3
            mp["4"] = R.drawable.avtar_4
            mp["5"] = R.drawable.avtar_5
            mp["6"] = R.drawable.avtar_6
        }

        fun getMid(id: String): Int {
            return if (map[id] == null) R.drawable.avtar_1
            else map[id]!!
        }
    }

}