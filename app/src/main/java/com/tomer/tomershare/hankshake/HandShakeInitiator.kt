package com.tomer.tomershare.hankshake

import android.util.Log
import com.tomer.tomershare.utils.Utils
import com.tomer.tomershare.utils.Utils.Companion.longFromBytearray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

class HandShakeInitiator(
    private val onReceive: (String, String, String) -> Unit
) {
    private var soc: Socket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun initFindingIp(ip: String) {
        Log.d("TAG--", "initFindingIp: $ip")
        val sts = ip.split(".")
        if (sts.size != 4) {
            clean()
            return
        }
        thread(start = true, isDaemon = true) {
            for (i in 1..255) {
                scope.launch {
                    val newIp = "${sts[0]}.${sts[1]}.${sts[2]}.$i"
                    try {
                        soc = Socket(newIp, Utils.Companion.HAND_SHAKE_PORT)
                        val sizeBytes = ByteArray(8)
                        soc?.inputStream?.read(sizeBytes)
                        val s = sizeBytes.longFromBytearray()
                        val nameBytes = ByteArray(s.toInt())
                        soc?.inputStream?.read(nameBytes)
                        val name = String(nameBytes, StandardCharsets.UTF_8)
                        onReceive(newIp, name.substring(1), name.substring(0, 1))
                        clean()
                        scope.cancel()
                    } catch (_: Exception) {
                    }
                }
            }
        }

    }

    private fun clean() {
        runCatching { soc?.close() }
        soc = null
    }
}