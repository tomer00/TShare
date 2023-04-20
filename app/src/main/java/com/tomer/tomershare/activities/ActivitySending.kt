package com.tomer.tomershare.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.tomer.tomershare.R
import com.tomer.tomershare.databinding.ActivitySendingBinding
import com.tomer.tomershare.modal.AppModal
import com.tomer.tomershare.utils.PathUtils.Companion.getFilePath
import com.tomer.tomershare.utils.PathUtils.Companion.getImagePath
import com.tomer.tomershare.utils.PathUtils.Companion.getVideoPath
import com.tomer.tomershare.utils.Utils
import com.tomer.tomershare.utils.Utils.Companion.bytesFromLong
import com.tomer.tomershare.utils.Utils.Companion.fileName
import java.io.File
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Enumeration
import kotlin.concurrent.thread

class ActivitySending : AppCompatActivity() {

    //region GLOBALS---->>>
    private val b by lazy { ActivitySendingBinding.inflate(layoutInflater) }


    private var soc: Socket? = null
    private var serverSocket: ServerSocket? = null

    private var time: Long = 0
    private var timeCurrent: Long = 0


    @Volatile
    private var totalBytes = 0L

    @Volatile
    private var currentBytes = 0L

    @Volatile
    private var canSend = true
    private var stopped = false

    @Volatile
    private var transferGoing = false

    //endregion GLOBALS---->>>

    fun Socket.sendString(string: String) {
        val strata: ByteArray = string.toByteArray(StandardCharsets.UTF_8)
        this.getOutputStream().write(strata.size.toLong().bytesFromLong())
        this.getOutputStream().write(strata)
    }


    //region ACTIVITY LIFECYCLES---->>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(b.root)


        //region HANDLE SELECTED URIS--->>
        val action = intent.action
        if (Intent.ACTION_SEND == action) {
            canSend = false
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_STREAM)
            if (uri != null) {
                thread {
                    handleFile(uri)
                    canSend = true
                }
            }
        } else if (Intent.ACTION_SEND_MULTIPLE == action) {
            canSend = false
            val uis = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            thread {
                for (uri in uis!!)
                    handleFile(uri)
                canSend = true
            }
        }
        //endregion HANDLE SELECTED URIS--->>

        thread {
            serverSocket = ServerSocket(Utils.SERVER_PORT)
            b.root.post {
//                "SCAN CODE".also { b.tvpText.text = it }
            }
            val pref = getSharedPreferences("NAME", MODE_PRIVATE)
            val ip = getIp()
            if (ip != "NOT") {
                b.root.post {
//                    b.tvIpShow.text = ip
                }
            }
            b.root.post {
//                b.imgQR.setImageBitmap(QRProvider.getQRBMP("${pref.getString("name", "Share")}::$ip::${pref.getString("icon", "1")}"))
            }
            try {
                soc = serverSocket!!.accept()
                soc!!.sendBufferSize = Utils.OUT_BUFF
                b.root.post {
//                    b.imgQRRota.clearAnimation()
//                    b.layQr.visibility = View.GONE
                }
                onOpen()
            } catch (_: Exception) {
            }
        }

    }


    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopped = true
        val parent = File(cacheDir, "temps").listFiles()
        parent?.forEach { f ->
            f.delete()
        }
    }

    // Init ui from barcode to sending and adding recyclerview Items..
    private fun intiUI() {

    }

    //endregion ACTIVITY LIFECYCLES---->>>


    //region DIALOGS---->>


    //endregion DIALOGS---->>


    //region SENDING FILES----->>>


    private fun onOpen() {
        while (true) {
            try {
                if (canSend) {
                    time = SystemClock.elapsedRealtime()
                    sendStart()
                    intiUI()
                    canSend = false
                } else SystemClock.sleep(20)
            } catch (_: Exception) {

            }
        }
    }

    private fun sendStart() {
        while (Utils.sendQueue.isNotEmpty()) {
            Log.d("TAG--", "sendStart: ${Utils.sendQueue.poll()?.file?.length()}")
        }
    }

    //endregion SENDING FILES----->>>


    //region HELPER FUNCTIONS---->>>

    private fun getIp(): String {
        var str = "NOT"
        val man = getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (man.isWifiEnabled) {
            try {
                val soc = DatagramSocket()
                soc.connect(InetAddress.getByName("8.8.8.8"), 12345)
                str = soc.localAddress.hostAddress?.toString() ?: "NOT"
                soc.close()
            } catch (_: Exception) {
            }
            if (str == "::") str = "NOT"
            return str
        }

        try {
            val info: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (info.hasMoreElements()) {
                val into: Enumeration<InetAddress> = info.nextElement().inetAddresses
                while (into.hasMoreElements()) {
                    val add = into.nextElement()
                    if (!add.isLoopbackAddress && add is Inet4Address) {
                        str = add.hostAddress?.toString() ?: "NOT"
                        if (!str.startsWith("192."))
                            str = "NOT"
                        return str
                    }
                }
            }
        } catch (_: Exception) {
        }
        return str
    }

    //endregion HELPER FUNCTIONS---->>>


    //region File URI HANDLERS---->>


    private fun handleFile(uri: Uri) {
        when (intent.type.toString()) {
            "video/*" -> {
                val path = uri.getVideoPath(applicationContext).toString()
                if (path != "null")
                    Utils.sendQueue.offer(AppModal(uri.fileName(), "0", File(path), ContextCompat.getDrawable(this, R.drawable.appfi)!!))
                else handelTempFiles(uri)
            }
            "image/*" -> {
                val path = uri.getImagePath(applicationContext).toString()
                if (path != "null")
                    Utils.sendQueue.offer(AppModal(uri.fileName(), "0", File(path), ContextCompat.getDrawable(this, R.drawable.appfi)!!))
                else handelTempFiles(uri)
            }
            "application/*" -> {
                val path = uri.getFilePath(applicationContext).toString()
                if (path != "null")
                    Utils.sendQueue.offer(AppModal(uri.fileName(), "0", File(path), ContextCompat.getDrawable(this, R.drawable.appfi)!!))
                else handelTempFiles(uri)
            }
            "text/*", "null" -> {
                finish()
            }
            else -> {
                var path = uri.getFilePath(applicationContext).toString()
                if (path != "null")
                    Utils.sendQueue.offer(AppModal(uri.fileName(), "0", File(path), ContextCompat.getDrawable(this, R.drawable.appfi)!!))
                else {
                    path = uri.getImagePath(applicationContext).toString()
                    if (path != "null")
                        Utils.sendQueue.offer(AppModal(uri.fileName(), "0", File(path), ContextCompat.getDrawable(this, R.drawable.appfi)!!))
                    else {
                        path = uri.getVideoPath(applicationContext).toString()
                        if (path != "null")
                            Utils.sendQueue.offer(AppModal(uri.fileName(), "0", File(path), ContextCompat.getDrawable(this, R.drawable.appfi)!!))
                        else {
                            handelTempFiles(uri)
                        }
                    }
                }
            }
        }

    }


    private fun handelTempFiles(uri: Uri) {

        Log.d("TAG--", "This uri is made TempFIles...: $uri")

        val parent = File(cacheDir, "temps")
        val name = uri.fileName()
        val f = File(parent, name)

        val ins = contentResolver.openInputStream(uri)
        try {
            ins.use { ins1 ->
                ins1!!.copyTo(f.outputStream())
            }
            if (f.length() > 0)
                Utils.sendQueue.offer(AppModal(name, "0", f, ContextCompat.getDrawable(this, R.drawable.appfi)!!))
        } catch (e: Exception) {
            Log.e("TAG--", "handelTempFiles: ", e)
        }

    }

    //endregion File URI HANDLERS---->>

}