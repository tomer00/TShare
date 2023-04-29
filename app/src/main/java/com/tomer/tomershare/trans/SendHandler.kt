package com.tomer.tomershare.trans

import com.tomer.tomershare.utils.Utils
import com.tomer.tomershare.utils.Utils.Companion.bytesFromLong
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.Socket

//Handle single File sending Completely
class SendHandler(private val soc: Socket, private val currFile: File, private val skipSize: Long, private val lis: (Int) -> Unit) : Runnable {

    private lateinit var fis: FileInputStream
    private val out: OutputStream = soc.getOutputStream()

    private val data = ByteArray(Utils.BUFF_SIZE)

    init {
        try {
            fis = FileInputStream(currFile)
            fis.skip(skipSize)
            this.run()
        } catch (_: Exception) {
        }
    }

    override fun run() {
        try {
            out.write((currFile.length() - skipSize).bytesFromLong())
            while (true) {
                val r: Int = fis.read(data)
                if (r == -1) break
                out.write(data, 0, r)
                out.flush()
                lis.invoke(r)
            }
            fis.close()
        } catch (e: Exception) {
            soc.close()
            try {
                fis.close()
            } catch (_: IOException) {
            }
        }
    }
}