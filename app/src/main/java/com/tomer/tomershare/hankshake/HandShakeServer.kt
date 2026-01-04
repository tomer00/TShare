package com.tomer.tomershare.hankshake

import com.tomer.tomershare.utils.Utils
import com.tomer.tomershare.utils.Utils.Companion.bytesFromLong
import java.net.ServerSocket
import kotlin.concurrent.thread

class HandShakeServer() {

    private val serverSocket = ServerSocket(Utils.Companion.HAND_SHAKE_PORT)
    private var isActive = true

    init {
        thread(start = true, isDaemon = true) {
            while (isActive) {
                try {
                    val soc =serverSocket.accept()
                    val d = "${Utils.Companion.myIcon}${Utils.Companion.myName}"
                    soc.outputStream.write(d.length.toLong().bytesFromLong())
                    soc.outputStream.write(d.toByteArray(Charsets.UTF_8))
                } catch (_: Exception) {
                }
            }
        }
    }

    fun stop() {
        isActive = false
        runCatching {
            serverSocket.close()
        }
    }
}