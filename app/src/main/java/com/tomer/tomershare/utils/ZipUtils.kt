package com.tomer.tomershare.utils

import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipUtils {
    companion object {


        fun File.toZip(tempFol: File): File {
            val outFile = File(tempFol, "${this.name}.fol")
            try {
                val zipOutputStream = ZipOutputStream(Files.newOutputStream(outFile.toPath()))
                zipFile(this, this.name, zipOutputStream)
                zipOutputStream.close()
            } catch (_: Exception) {
            }
            return outFile
        }


        @Throws(Exception::class)
        private fun zipFile(fileToZip: File, fileName: String, zipOut: ZipOutputStream) {
            if (fileToZip.isHidden) {
                return
            }
            if (fileToZip.isDirectory) {
                if (fileName.endsWith(" / ")) {
                    zipOut.putNextEntry(ZipEntry(fileName))
                    zipOut.closeEntry()
                } else {
                    zipOut.putNextEntry(ZipEntry(fileName + File.separator))
                    zipOut.closeEntry()
                }
                val children = fileToZip.listFiles()!!
                for (childFile in children) {
                    zipFile(childFile, fileName + File.separator + childFile.name, zipOut)
                }
                return
            }
            val fis = FileInputStream(fileToZip)
            val zipEntry = ZipEntry(fileName)
            zipOut.putNextEntry(zipEntry)
            val bytes = ByteArray(1024)
            var length: Int
            while (fis.read(bytes).also { length = it } >= 0) {
                zipOut.write(bytes, 0, length)
            }
            fis.close()
        }


        fun File.unZipFile(): File {
            val destDir = File(Environment.getExternalStorageDirectory(), "tshare")

            // creating an output directory if it doesn't exist already
            if (!destDir.exists()) destDir.mkdirs()
            val fis: FileInputStream
            // buffer to read and write data in the file
            val buffer = ByteArray(1024)
            try {
                fis = FileInputStream(this)
                val zipInputStream = ZipInputStream(fis)
                var zipEntry = zipInputStream.nextEntry
                while (zipEntry != null) {
                    val fileName = zipEntry.name
                    val newFile = File(destDir, fileName)
                    if (fileName.endsWith("/")) // this is directory
                        newFile.mkdirs()
                    else {
                        // create directories for sub directories in zip
                        val fos = FileOutputStream(newFile)
                        var len: Int
                        while (zipInputStream.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                        fos.close()
                    }

                    //  close this ZipEntry
                    zipInputStream.closeEntry()
                    zipEntry = zipInputStream.nextEntry
                }
                // close last ZipEntry
                zipInputStream.closeEntry()
                zipInputStream.close()
                fis.close()
            } catch (_: IOException) {
            }
            return destDir
        }
    }
}