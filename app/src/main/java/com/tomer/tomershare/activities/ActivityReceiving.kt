package com.tomer.tomershare.activities

import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.tomer.tomershare.databinding.ActivityRecivingBinding
import com.tomer.tomershare.trans.RecHandler
import com.tomer.tomershare.utils.Utils
import com.tomer.tomershare.utils.Utils.Companion.bytesFromLong
import com.tomer.tomershare.utils.Utils.Companion.longFromBytearray
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

class ActivityReceiving : AppCompatActivity() {

    //region GLOBALS--->>>
    private val b by lazy { ActivityRecivingBinding.inflate(layoutInflater) }

    private var soc: Socket? = null

    private val bytesLong = ByteArray(8)
    private var time: Long = 0
    private var timeCurrent: Long = 0


    @Volatile
    private var totalBytes = 0L

    @Volatile
    private var currFileBytes = 0L

    @Volatile
    private var currRecBytes = 0L

    private var stopped = false

    @Volatile
    private var transferGoing = false

    private val parentFolder = File(Environment.getExternalStorageDirectory(), "tshare")

    //endregion GLOBALS--->>>


    //region ACTIVITY LIFECYCLES---->>>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(b.root)
    }


    override fun onDestroy() {
        super.onDestroy()

    }

    //endregion ACTIVITY LIFECYCLES---->>>


    //region RECEIVING FILES---->>

    private fun reconnect() {
        runOnUiThread {
            thread {
                try {
                    soc = Socket()
                    soc!!.bind(null)
                    soc!!.connect(InetSocketAddress(Utils.ADDRESS, Utils.SERVER_PORT))
                    time = SystemClock.elapsedRealtime()
                    while (true) {
                        soc!!.getInputStream().read(bytesLong, 0, 8)
                        val size: Long = bytesLong.longFromBytearray()


                        if (size > 260) {
//                            onFinish()
                            break
                        }

                        val nameBytes = ByteArray(size.toInt())

                        soc!!.getInputStream().read(nameBytes)


                        // name of file currently receiving.......
                        val fileReceiving = String(nameBytes, StandardCharsets.UTF_8)

                        if (fileReceiving == "FINISH") {
//                            onFinish()
                            break
                        }

                        currRecBytes = 0
                        currFileBytes = 0
                        val f = File(parentFolder, fileReceiving)


                        // sending cursor for this file
                        if (f.exists()) {
                            soc!!.getOutputStream().write(f.length().bytesFromLong())
                            currFileBytes = f.length()
                            currRecBytes = f.length()
                        } else soc!!.getOutputStream().write((0L).bytesFromLong())


                        // receiving the length remaining that we will be receiving...
                        soc!!.getInputStream().read(bytesLong, 0, 8)
                        val sizeReceiving = bytesLong.longFromBytearray()


                        currFileBytes += sizeReceiving

//                        onNewFIle(fileReceiving)


                        RecHandler(soc!!.getInputStream(), f, { long ->
                            currFileBytes += long
                            totalBytes += long

                            runOnUiThread {
                                try {
                                    val p = ((currFileBytes * 100) / currRecBytes).toFloat()
//                                    b.progRec.progress = p.toInt()
                                } catch (_: Exception) {
                                }
                            }
                        }, sizeReceiving)
                    }

                } catch (e: Exception) {
                    try {
//                        runOnUiThread {
//                            if (!stopped)
//                                if (!networkDia.isShowing && b.frLay.scaleX == 2f) {
//                                    b.btShowQR.visibility = View.VISIBLE
//                                    b.btShowQR.animate().apply {
//                                        scaleY(1f)
//                                        scaleX(1f)
//                                        duration = 400
//                                        interpolator = OvershootInterpolator(1.2f)
//                                        start()
//                                    }
//                                }
//                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }


//    private fun onNewFIle(name: String) {
//        runOnUiThread {
//            b.apply {
//                imgCenter.rotate()
//                if (frLay.scaleX == 2f) {
//                    val a = b.imgNoAnim.drawable as AnimationDrawable
//                    a.stop()
//                    b.imgNoAnim.visibility = View.GONE
//                    b.tvDetail.visibility = View.GONE
//
//                    b.btShowQR.animate().apply {
//                        scaleY(0f)
//                        scaleX(0f)
//                        translationYBy(200F)
//                        duration = 600
//                        withEndAction { b.btShowQR.visibility = View.GONE }
//                        start()
//                    }
//
//                    frLay.animate().apply {
//                        x(imgRotHelper.x)
//                        y(imgRotHelper.y)
//                        scaleY(0.8f)
//                        scaleX(0.8f)
//                        duration = 600
//                        start()
//                    }
//                    progRec.animate().apply {
//                        scaleX(1f)
//                        duration = 600
//                        start()
//                    }
//                    frLay.setOnClickListener(null)
//                }
//            }
//
//            val list = mutableListOf<String>()
//            list.addAll(adapMsg.currentList)
//            list.add("$name,.,rec")
//            adapMsg.submitList(list)
//            b.tRv.smoothScrollToPosition(list.size - 1)
//        }
//    }
//
//    private fun onFinish() {
//        startSending = false
//        runOnUiThread {
//            if (!stopped) {
//                finishD.show()
//                b.imgCenter.clearAnimation()
//            }
//        }
//    }

    //endregion RECEIVING FILES---->>
}