package com.tomer.tomershare.trans

import com.tomer.tomershare.utils.Utils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream


//Handle single File Receiving Completely
class RecHandler(private val ins: InputStream, outFile: File, private var lis: RecLis, private var remainSize: Long) : Runnable {

    private lateinit var out: FileOutputStream

    private val data = ByteArray(Utils.BUFF_SIZE)
    private var r = 0

    init {
        try {
            out = if (outFile.exists())
                FileOutputStream(outFile, true)
            else FileOutputStream(outFile)

            this.run()
        } catch (e: Exception) {
        }

    }

    override fun run() {
        try {
            while (remainSize > 0) {
                r = if (remainSize <= Utils.BUFF_SIZE) ins.read(data, 0, remainSize.toInt())
                else ins.read(data, 0, Utils.BUFF_SIZE)
                remainSize -= r
                out.write(data, 0, r)
                lis.onUpdate(r)
            }
        } catch (e: Exception) {
        }
        out.flush()
        out.close()
    }

    interface RecLis {
        fun onUpdate(long: Int)
    }
}