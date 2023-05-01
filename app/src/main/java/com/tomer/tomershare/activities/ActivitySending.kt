package com.tomer.tomershare.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.tomer.tomershare.R
import com.tomer.tomershare.adap.AdaptMsg
import com.tomer.tomershare.databinding.ActivitySendingBinding
import com.tomer.tomershare.modal.AppModal
import com.tomer.tomershare.modal.TransferModal
import com.tomer.tomershare.trans.SendHandler
import com.tomer.tomershare.utils.PathUtils.Companion.getFilePath
import com.tomer.tomershare.utils.PathUtils.Companion.getImagePath
import com.tomer.tomershare.utils.PathUtils.Companion.getVideoPath
import com.tomer.tomershare.utils.QRProvider
import com.tomer.tomershare.utils.Repo
import com.tomer.tomershare.utils.Utils
import com.tomer.tomershare.utils.Utils.Companion.bytesFromLong
import com.tomer.tomershare.utils.Utils.Companion.fileName
import com.tomer.tomershare.utils.Utils.Companion.longFromBytearray
import com.tomer.tomershare.utils.Utils.Companion.rotate
import java.io.File
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Enumeration
import java.util.regex.Pattern
import kotlin.concurrent.thread

class ActivitySending : AppCompatActivity() {

    //region GLOBALS---->>>
    private val b by lazy { ActivitySendingBinding.inflate(layoutInflater) }
    private val adapSend by lazy { AdaptMsg(this, this::closeCurrFile) }
    private val list = mutableListOf<TransferModal>()

    private var soc: Socket? = null
    private var serverSocket: ServerSocket? = null

    private var time: Long = 0
    private var timeCurrent: Long = 0

    @Volatile
    private var index = -1
    private val bytesLong = ByteArray(8)

    @Volatile
    private var currTotalBytes = 0L

    @Volatile
    private var currentBytes = 0L

    @Volatile
    private var finalTotalBytes = 0L

    @Volatile
    private var canSend = true
    private var stopped = false

    @Volatile
    private var transferGoing = false


    private var avatar = "1"
    private var phoneName = "Android"
    //endregion GLOBALS---->>>

    @Throws(Exception::class)
    private fun Socket.sendString(string: String) {
        val strata: ByteArray = string.toByteArray(StandardCharsets.UTF_8)
        this.getOutputStream().write(strata.size.toLong().bytesFromLong())
        this.getOutputStream().write(strata)
        this.getOutputStream().flush()
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
                    b.tvIpShow.text = ip
                    b.imgQRRota.rotate()
                    b.imgAvatarReceiver.rotate()
                }
            }
            b.root.post {
                b.imgQR.setImageBitmap(
                    QRProvider.getQRBMP(
                        "${pref.getString("name", "Share")}::$ip::${pref.getString("icon", "1")}",
                        ContextCompat.getColor(this, R.color.co_main)
                    )
                )
            }
            try {
                soc = serverSocket!!.accept()
                soc!!.sendBufferSize = Utils.OUT_BUFF
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
        Utils.sendQueue.clear()
        closeSockets()
        val parent = File(cacheDir, "temps").listFiles()
        parent?.forEach { f ->
            f.delete()
        }
    }

    // Init ui from barcode to sending and adding recyclerview Items..
    private fun intiUI() {
        Utils.sendQueue.forEach { mod ->
            list.add(TransferModal(mod.name))
        }
        b.root.post {
            b.imgQRRota.clearAnimation()
            b.layQr.visibility = View.GONE
            b.tRv.adapter = adapSend
            adapSend.submitList(list)
            b.imgAvatarReceiver.apply {
                clearAnimation()
                rotation = 0f
                setImageDrawable(ContextCompat.getDrawable(this@ActivitySending, Repo.getMid(avatar)))
            }
            "Sending to $phoneName's Phone".also { b.tvSendingName.text }
        }
    }

    private fun finishUI() {
        runOnUiThread {
            b.apply {
                progTop.visibility = View.GONE
                finishView.visibility = View.VISIBLE
                val li = mutableListOf<TransferModal>()
                adapSend.currentList.forEach { mod ->
                    li.add(TransferModal(mod.fileName))
                }
                adapSend.submitList(li)
            }
        }
    }


    //endregion ACTIVITY LIFECYCLES---->>>


    //region SENDING FILES----->>>

    @Throws(Exception::class)
    private fun readPhoneName() {
        soc!!.getInputStream().read(bytesLong, 0, 8)
        val size: Long = bytesLong.longFromBytearray()
        val nameBytes = ByteArray(size.toInt())
        soc!!.getInputStream().read(nameBytes, 0, size.toInt())
        // name of file currently receiving....... // Phone NAmeid{3}
        val phoneNameAndAvatar = String(nameBytes, StandardCharsets.UTF_8)

        runOnUiThread {
            avatar = phoneNameAndAvatar[size.toInt() - 1].toString()
            phoneName = phoneNameAndAvatar.substring(0, size.toInt() - 1)
        }
    }

    private fun closeCurrFile() {
        if (!transferGoing) return
        soc!!.close()
    }

    private fun reListen() {
        if (stopped) {
            closeSockets()
            return
        }
        try {
            if (soc != null) soc!!.close()
            b.root.postDelayed({
                if (!transferGoing) {
                    closeSockets()
                }
            }, 1000)
            soc = serverSocket!!.accept()
            runOnUiThread {
                thread {
                    sendData()
                }
            }
        } catch (_: Exception) {
            finishUI()
        }
    }

    private fun onSendingDone() {
        soc!!.sendString("FINISH")
        finishUI()
    }


    private fun onOpen() {
        while (true) {
            try {
                if (canSend) {
                    time = SystemClock.elapsedRealtime()
                    readPhoneName()
                    intiUI()
                    sendData()
                    canSend = false
                    break
                } else SystemClock.sleep(20)
            } catch (_: Exception) {
                finishUI()
                break
            }
        }
    }


    // this is always called on a separate thread
    @Throws(Exception::class)
    private fun sendData() {

        while (Utils.sendQueue.isNotEmpty()) {
            val appModal = Utils.sendQueue.poll()!!
            runOnUiThread {
                index++
                adapSend.currentList[index].isTrans = true
                adapSend.notifyItemChanged(index)
                try {
                    adapSend.currentList[index - 1].isTrans = false
                    adapSend.notifyItemChanged(index - 1)
                } catch (_: Exception) {
                }
                b.tRv.smoothScrollToPosition(list.size - 1)
                b.progTop.updateProg(0f)
            }

            // sending the file name length and name bytes itself...
            try {
                soc!!.sendString(appModal.name)
            } catch (_: Exception) {
                closeSockets()
                finishUI()
                break
            }

            //getting the skip cursor from that file as long....
            soc!!.getInputStream().read(bytesLong, 0, 8)
            val cursor: Long = bytesLong.longFromBytearray()

            currTotalBytes = appModal.file.length()
            currentBytes += cursor

            transferGoing = true
            SendHandler(soc!!, appModal.file, cursor) { long ->
                finalTotalBytes += long
                currentBytes += long
                try {
                    val p = ((currentBytes * 100) / currTotalBytes).toFloat()
                    b.progTop.post { b.progTop.updateProg(p / 100) }
                } catch (_: Exception) {
                }
            }
            transferGoing = false
            if (soc!!.isClosed) {
                reListen()
                return
            }
        }
        onSendingDone()
    }

    private fun closeSockets() {
        try {
            if (soc != null) soc!!.close()
            if (serverSocket != null) serverSocket!!.close()
        } catch (_: Exception) {
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
                        if (!str.startsWith("192.168."))
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

        //for Xiaomi Phone's File Manager....
        if (uri.path!!.contains("/external_files/")) {
            val sts: Array<String> = Pattern.compile("/external_files/").split(uri.path!!)
            val f = File(Environment.getExternalStorageDirectory(), sts[1])
            Utils.sendQueue.offer(AppModal(f.name, "0", f, ContextCompat.getDrawable(this, R.drawable.appfi)!!))
            return
        }

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
        }

    }

    //endregion File URI HANDLERS---->>

}
