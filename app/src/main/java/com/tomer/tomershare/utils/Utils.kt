package com.tomer.tomershare.utils

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
//import com.tomer.tomershare.modal.AppModal
import java.io.File
import java.net.Socket
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.math.pow

class Utils {
    companion object {

        const val BUFF_SIZE = 1048576
        const val OUT_BUFF = 33554432
        const val SERVER_PORT = 8012
        var ADDRESS = "192.168.43.1"

        fun Long.bytesFromLong(): ByteArray {
            return ByteBuffer.allocate(8).putLong(this).array()
        }

        fun ByteArray.longFromBytearray(): Long {
            return ByteBuffer.wrap(this).long
        }

        fun Socket.sendString(string: String){
            val strata: ByteArray = string.toByteArray(StandardCharsets.UTF_8)
            this.getOutputStream().write(strata.size.toLong().bytesFromLong())
            this.getOutputStream().write(strata)
        }


        fun Int.px(den:Float):Int = (this*den).toInt()

        fun View.rotate(){
            val rotateAnim = RotateAnimation(0f, 359f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
                duration = 1000
                repeatCount = Animation.INFINITE
                interpolator = LinearInterpolator()
                repeatMode = Animation.RESTART
            }
            this.startAnimation(rotateAnim)
        }

        fun View.haptic(){
            if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.R) this.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK,HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        }

//        val files = ArrayList<AppModal>(1)

        fun String.decode(): String = URLDecoder.decode(this, StandardCharsets.UTF_8.toString())

        fun humanReadableSize(apkSize: Long): String {
            return  when {
                apkSize < 1024 -> String.format("%1$.2f B", apkSize.toDouble())
                apkSize < 1024.0.pow(2.0) -> String.format("%1$.2f KB", (apkSize / 1024).toDouble())
                apkSize < 1024.0.pow(3.0) -> String.format("%1$.2f MB", apkSize / 1024.0.pow(2.0))
                else -> String.format("%1$.2f GB", apkSize / 1024.0.pow(3.0))
            }
        }

        fun allFiles(f: File): Array<File> = f.listFiles() as Array<File>
    }
}