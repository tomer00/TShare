package com.tomer.tomershare.trans

import com.tomer.tomershare.utils.Utils
import com.tomer.tomershare.utils.Utils.Companion.bytesFromLong
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream

//Handle single File sending Completely
class SendHandler(private val out: OutputStream, private val currFile: File, private val skipSize: Long, private val lis: SendLis) : Runnable {

    private lateinit var fis: FileInputStream

    private val data = ByteArray(Utils.BUFF_SIZE)

    init {
        try {
            fis = FileInputStream(currFile)
            fis.skip(skipSize)
            this.run()
        } catch (e: Exception) {
        }
    }

    override fun run() {
        try {
            out.write((currFile.length() - skipSize).bytesFromLong())
            while (true) {
                val r: Int = fis.read(data)
                if (r == -1) break
                out.write(data, 0, r)
                lis.onUpdate(r)
            }
            fis.close()
        } catch (e: Exception) {
            try {
                fis.close()
            } catch (e: IOException) {
            }
        }
    }

    interface SendLis {
        fun onUpdate(long: Int)
    }
}