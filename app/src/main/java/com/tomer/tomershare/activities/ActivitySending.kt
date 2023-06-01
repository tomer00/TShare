package com.tomer.tomershare.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
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
import com.tomer.tomershare.utils.Repo
import com.tomer.tomershare.utils.Utils
import com.tomer.tomershare.utils.Utils.Companion.bytesFromLong
import com.tomer.tomershare.utils.Utils.Companion.fileName
import com.tomer.tomershare.utils.Utils.Companion.longFromBytearray
import com.tomer.tomershare.utils.Utils.Companion.rotate
import com.tomer.tomershare.utils.ZipUtils.Companion.toZip
import com.tomer.tomershare.views.EndPosterProvider.Companion.getEndPoster
import com.tomer.tomershare.views.QRProvider
import com.tomer.tomershare.widget.WidgetService
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
    private val adaptSend by lazy { AdaptMsg(this, this::closeCurrFile) }
    private val list = mutableListOf<TransferModal>()

    private var soc: Socket? = null
    private var serverSocket: ServerSocket? = null

    private var time: Long = 0

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
    private var canSend = false
    private var stopped = false
    private var isService = false

    private val serIntent by lazy { Intent(this, WidgetService::class.java) }

    private val timer = object : CountDownTimer(400, 400) {
        override fun onTick(p0: Long) {

        }

        override fun onFinish() {
            val p = try {
                ((currentBytes.toDouble()) / currTotalBytes).toFloat()
            } catch (_: Exception) {
                0f
            }
            b.progTop.post {
                b.progTop.updateProg(p)
                if (isService) {
                    serIntent.putExtra("prog", p)
                    startService(serIntent)
                }
                this.start()
            }
        }
    }

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
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_STREAM)
            if (uri != null) {
                thread {
                    handleFile(uri)
                    canSend = true
                }
            }
        } else if (Intent.ACTION_SEND_MULTIPLE == action) {
            val uis = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            thread {
                for (uri in uis!!) handleFile(uri)
                canSend = true
            }
        }

        //check if there is any folder in Selected files...
        thread {
            val tempFol = File(cacheDir, "temps")
            if (!tempFol.exists()) tempFol.mkdirs()
            Utils.sendQueue.forEach { mod ->
                if (mod.file.isDirectory) {
                    mod.file = mod.file.toZip(tempFol)
                    mod.name = mod.name + ".fol"
                }
            }
            canSend = true
        }

        //endregion HANDLE SELECTED URIS--->>


        setNewQr()

        thread {
            serverSocket = ServerSocket(Utils.SERVER_PORT)
            try {
                soc = serverSocket!!.accept()
                soc!!.sendBufferSize = Utils.OUT_BUFF
                onOpen()
            } catch (_: Exception) {
            }
        }

        b.imgQRRota.rotate()
        b.imgAvatarReceiver.rotate()
        if (!Settings.canDrawOverlays(this)) reqOverLay()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (transferGoing) {
            val action = intent.action
            if (Intent.ACTION_SEND == action) {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_STREAM)
                if (uri != null) {
                    thread {
                        handleFile(uri)
                    }
                }
            } else if (Intent.ACTION_SEND_MULTIPLE == action) {
                val uis = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                thread {
                    for (uri in uis!!) handleFile(uri)
                }
            }
        }
    }


    override fun onResume() {
        super.onResume()
        stopService(serIntent)
        isService = false
    }

    override fun onPause() {
        super.onPause()
        if (transferGoing && !isFinishing) {
            serIntent.putExtra("send",true)
            startService(serIntent)
            serIntent.removeExtra("send")
            var index = 0
            adaptSend.currentList.forEachIndexed { ind, transferModal ->
                if (transferModal.isTrans) index = ind
            }
            val name = adaptSend.currentList[index].fileName
            serIntent.putExtra("name", name)
            serIntent.putExtra("ext", name.subSequence(name.lastIndexOf('.') + 1, name.length))
            startService(serIntent)
            serIntent.removeExtra("name")
            isService = true
        }
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

    private fun setNewQr() {
        if (stopped) return
        thread {
            val ip = getIp()
            runOnUiThread {
                val pref = getSharedPreferences("NAME", MODE_PRIVATE)
                b.tvIpShow.text = if (ip != "NOT") ip else ""
                b.imgQR.setImageBitmap(
                    QRProvider.getQRBMP(
                        "${pref.getString("name", "Share")}::$ip::${pref.getString("icon", "1")}",
                        ContextCompat.getColor(this, R.color.co_main)
                    )
                )
                if (ip == "NOT")
                    b.root.postDelayed(this::setNewQr, 2000)
            }
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
            b.tRv.adapter = adaptSend
            adaptSend.submitList(list)
            b.imgAvatarReceiver.apply {
                clearAnimation()
                rotation = 0f
                setImageDrawable(ContextCompat.getDrawable(this@ActivitySending, Repo.getMid(avatar)))
            }
            "Sending to $phoneName".also { b.tvSendingName.text = it }
        }
    }

    private fun finishUI() {
        runOnUiThread {
            b.apply {
                root.keepScreenOn = false
                if (isService) {
                    serIntent.removeExtra("name")
                    serIntent.putExtra("done", true)
                    startService(serIntent)
                    isService = false
                }

                progTop.visibility = View.GONE

                val timeTaken = SystemClock.elapsedRealtime() - time
                val volume = Utils.humanReadableSize(finalTotalBytes)
                try {
                    val speed = String.format("%1$.2f", (finalTotalBytes / 1048576f) / (timeTaken / 1000f))
                    finishView.setImageBitmap(this@ActivitySending.getEndPoster(speed, volume))
                } catch (_: Exception) {
                    finishView.setImageBitmap(this@ActivitySending.getEndPoster("0.00", volume))
                }
                finishView.visibility = View.VISIBLE

                val tme = (timeTaken / 1000).toInt()
                "Sent in just ${tme / 60} min and ${tme % 60} seconds...".also { tvSendingName.text = it }
                imgAvatarReceiver.setImageDrawable(ContextCompat.getDrawable(this@ActivitySending, R.drawable.ic_clock))
                val li = mutableListOf<TransferModal>()
                adaptSend.currentList.forEach { mod ->
                    li.add(TransferModal(mod.fileName))
                }
                adaptSend.submitList(li)
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
        if (stopped) return
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
        closeSockets()
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
                adaptSend.currentList[index].isTrans = true
                adaptSend.notifyItemChanged(index)
                try {
                    adaptSend.currentList[index - 1].isTrans = false
                    adaptSend.notifyItemChanged(index - 1)
                } catch (_: Exception) {
                }
                b.tRv.smoothScrollToPosition(index)
                b.progTop.updateProg(0f)
            }

            // sending the file name length and name bytes itself...
            try {
                soc!!.sendString(appModal.name)
                //getting the skip cursor from that file as long....
                soc!!.getInputStream().read(bytesLong, 0, 8)
            } catch (_: Exception) {
                closeSockets()
                finishUI()
                break
            }

            val cursor: Long = bytesLong.longFromBytearray()

            currTotalBytes = appModal.file.length()
            currentBytes = cursor

            b.root.post {
                val p = try {
                    ((currentBytes * 100) / currTotalBytes).toFloat()
                } catch (_: Exception) {
                    0f
                }
                b.progTop.updateProg(p / 100)
                if (isService) {
                    serIntent.putExtra("name", appModal.name)
                    serIntent.putExtra("ext", appModal.file.extension)
                    startService(serIntent)
                    serIntent.removeExtra("name")
                    serIntent.putExtra("prog", p / 100)
                    startService(serIntent)
                }
            }
            timer.start()

            transferGoing = true
            SendHandler(soc!!, appModal.file, cursor) { long ->
                finalTotalBytes += long
                currentBytes += long
            }
            transferGoing = false
            timer.cancel()
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

    private fun reqOverLay() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${packageName}")
        )
        startActivityForResult(intent, 101)
    }

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
                        if (!str.startsWith("192.168.")) str = "NOT"
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
                val path = uri.getVideoPath(applicationContext)
                if (path.first != "null") Utils.sendQueue.offer(AppModal(path.second, "0", File(path.first), ContextCompat.getDrawable(this, R.drawable.appfi)!!))
                else handelTempFiles(uri)
            }
            "image/*" -> {
                val path = uri.getImagePath(applicationContext)
                if (path.first != "null") Utils.sendQueue.offer(AppModal(path.second, "0", File(path.first), ContextCompat.getDrawable(this, R.drawable.appfi)!!))
                else handelTempFiles(uri)
            }
            "application/*" -> {
                val path = uri.getFilePath(applicationContext)
                if (path.first != "null") Utils.sendQueue.offer(AppModal(path.second, "0", File(path.first), ContextCompat.getDrawable(this, R.drawable.appfi)!!))
                else handelTempFiles(uri)
            }
            "text/*", "null" -> {
                finish()
            }
            else -> {
                var path = uri.getFilePath(applicationContext)
                if (path.first != "null") Utils.sendQueue.offer(AppModal(path.second, "0", File(path.first), ContextCompat.getDrawable(this, R.drawable.appfi)!!))
                else {
                    path = uri.getImagePath(applicationContext)
                    if (path.first != "null") Utils.sendQueue.offer(AppModal(path.second, "0", File(path.first), ContextCompat.getDrawable(this, R.drawable.appfi)!!))
                    else {
                        path = uri.getVideoPath(applicationContext)
                        if (path.first != "null") Utils.sendQueue.offer(AppModal(path.second, "0", File(path.first), ContextCompat.getDrawable(this, R.drawable.appfi)!!))
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
        if (!parent.exists()) parent.mkdirs()
        val name = uri.fileName()
        val f = File(parent, name)

        val ins = contentResolver.openInputStream(uri)
        try {
            ins.use { ins1 ->
                ins1!!.copyTo(f.outputStream())
            }
            if (f.length() > 0) Utils.sendQueue.offer(AppModal(name, "0", f, ContextCompat.getDrawable(this, R.drawable.appfi)!!))
        } catch (_: Exception) {
        }

    }

    //endregion File URI HANDLERS---->>

}
