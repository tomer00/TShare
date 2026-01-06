package com.tomer.tomershare.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.tomer.tomershare.R
import com.tomer.tomershare.adap.AdaptMsg
import com.tomer.tomershare.databinding.ActivitySendingBinding
import com.tomer.tomershare.modal.AppModal
import com.tomer.tomershare.modal.TransferModal
import com.tomer.tomershare.trans.SendHandler
import com.tomer.tomershare.utils.CipherUtils
import com.tomer.tomershare.hankshake.HandShakeServer
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
import com.tomer.tomershare.views.EndPosterProvider.Companion.getEndPoster
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
    private val adaptSend by lazy { AdaptMsg(this, this::onClickRvItem) }
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
    private val handShakeServer = HandShakeServer()


    private var avatar = "1"
    private var phoneName = "Android"

    private var blackBmp = createBitmap(1, 1)
    //endregion GLOBALS---->>>

    @Throws(Exception::class)
    private fun Socket.sendString(string: String) {
        val strata: ByteArray = string.toByteArray(StandardCharsets.UTF_8)
        this.getOutputStream().write(strata.size.toLong().bytesFromLong())
        this.getOutputStream().write(strata)
        this.getOutputStream().flush()
    }


    @Volatile
    private var isMetricThread = false

    @Volatile
    private var lastTotalBytes = 0L
    private val metricThread = Thread {
        while (isMetricThread) {
            SystemClock.sleep(1000)
            val sentIn100 = finalTotalBytes - lastTotalBytes
            lastTotalBytes = finalTotalBytes
            val speed = Utils.humanReadableSize(sentIn100)
            val timeRerm = try {
                (currTotalBytes - currentBytes).div(sentIn100)
            } catch (e: Exception) {
                -1
            }
            runOnUiThread {
                "$speed/s".also { b.tvSpeed.text = it }
                b.tvTimer.text = timerString(timeRerm)
            }
        }
    }

    private fun timerString(secs: Long): String {
        return if (secs == -1L || secs > 12000) "..-.."
        else if (secs <= 59) "$secs Sec"
        else if (secs >= 3600) "${secs.div(3600)} H ${secs.mod(3600).div(60)} M"
        else "${secs.div(60)} M ${secs.mod(60)} S"
    }


    //region ACTIVITY LIFECYCLES---->>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(b.root)

        //region HANDLE SELECTED URIS--->>
        val action = intent.action
        if (Intent.ACTION_SEND == action) {
            val uri =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(
                    Intent.EXTRA_STREAM,
                    Uri::class.java
                )
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

        thread {
            val folList = mutableListOf<File>()
            val i = Utils.sendQueue.iterator()
            while (i.hasNext()) {
                val m = i.next();
                if (m.file.isDirectory) {
                    folList.add(m.file)
                    i.remove()
                }
            }

            setAllFilesInFolderToQueue(folList)
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
                val uri =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(
                        Intent.EXTRA_STREAM,
                        Uri::class.java
                    )
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
            serIntent.putExtra("send", true)
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

    private var prevIp = "NOT"
    private fun setNewQr() {
        if (stopped || transferGoing) return
        thread {
            val ip = getIp()
            if (ip != prevIp)
                runOnUiThread {
                    prevIp = ip
                    b.tvIpShow.text = if (ip != "NOT") ip else {
                        val msg = "Please Connect To\nWifi or open Hotspot"
                        val ss = SpannableString(msg)

                        val wifiClick = object : ClickableSpan() {
                            override fun onClick(widget: View) {
                                runCatching { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
                            }

                            override fun updateDrawState(ds: TextPaint) {
                                ds.color = "#FFFF1744".toColorInt()
                                ds.isUnderlineText = true
                            }
                        }

                        val hotspotClick = object : ClickableSpan() {
                            override fun onClick(widget: View) {
                                runCatching { startActivity(Intent("android.settings.TETHER_SETTINGS")) }
                                    .getOrElse { runCatching { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) } }
                            }

                            override fun updateDrawState(ds: TextPaint) {
                                ds.color = "#FFFF1744".toColorInt()
                                ds.isUnderlineText = true
                            }
                        }

                        val wifiStart = msg.indexOf("Wifi")
                        if (wifiStart != -1) {
                            ss.setSpan(
                                wifiClick,
                                wifiStart,
                                wifiStart + 4,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }

                        val hotspotStart = msg.indexOf("Hotspot")
                        if (hotspotStart != -1) {
                            ss.setSpan(
                                hotspotClick,
                                hotspotStart,
                                hotspotStart + 7,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }

                        b.tvIpShow.movementMethod = LinkMovementMethod.getInstance()
                        b.tvIpShow.highlightColor = Color.TRANSPARENT
                        ss
                    }
                    b.imgQR.setImageBitmap(
                        QRProvider.getQRBMP(
                            "${Utils.myName}::$ip::${Utils.myIcon}",
                            ContextCompat.getColor(this, R.color.co_main)
                        )
                    )
                    val str = CipherUtils.performString(
                        "${Utils.myName}::$ip::${Utils.myIcon}"
                    )
                    Glide.with(this)
                        .asBitmap()
                        .load("https://qr.devhimu.in?type=6&size=800&data=${str}")
                        .skipMemoryCache(true)
                        .into(
                            object : CustomTarget<Bitmap>() {
                                override fun onResourceReady(
                                    resource: Bitmap,
                                    transition: Transition<in Bitmap>?
                                ) {
                                    if (blackBmp.width == 1)
                                        b.imgQR.setImageBitmap(resource)
                                }

                                override fun onLoadCleared(placeholder: Drawable?) {
                                }

                            })
                    b.imgQR.setOnClickListener {
                        if (blackBmp.width != 1) return@setOnClickListener
                        blackBmp = QRProvider.getQRBMPBlack(
                            "${Utils.myName}::$ip::${Utils.myIcon}"
                        )
                        b.imgQR.setImageBitmap(blackBmp)
                    }
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
                setImageDrawable(
                    ContextCompat.getDrawable(
                        this@ActivitySending,
                        Repo.getMid(avatar)
                    )
                )
            }
            "Sending to $phoneName".also { b.tvSendingName.text = it }
            b.layMetrics.visibility = View.VISIBLE
        }
        isMetricThread = true
        metricThread.start()
    }

    private fun finishUI() {
        isMetricThread = false
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
                    val speed =
                        String.format("%1$.2f", (finalTotalBytes / 1048576f) / (timeTaken / 1000f))
                    finishView.setImageBitmap(this@ActivitySending.getEndPoster(speed, volume))
                } catch (_: Exception) {
                    finishView.setImageBitmap(this@ActivitySending.getEndPoster("0.00", volume))
                }
                finishView.visibility = View.VISIBLE

                val tme = (timeTaken / 1000).toInt()
                if (tme < 60) "Sent in just $tme seconds...".also { tvSendingName.text = it }
                else "Sent in just ${tme / 60} min and ${tme % 60} seconds...".also {
                    tvSendingName.text = it
                }
                imgAvatarReceiver.setImageDrawable(
                    ContextCompat.getDrawable(
                        this@ActivitySending,
                        R.drawable.ic_clock
                    )
                )
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

    private fun onClickRvItem(isClose: Boolean, pos: Int) {
        if (isClose) {
            if (!transferGoing) return
            soc!!.close()
            return
        }
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
                    handShakeServer.stop()
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
    private val drDef by lazy { ContextCompat.getDrawable(this, R.drawable.logo) }

    private fun setAllFilesInFolderToQueue(list: List<File>) {
        for (f in list) recAdd("", f)
    }

    private fun recAdd(par: String, file: File) {
        if (file.isDirectory) {
            val files = file.listFiles()
            for (f in files!!) {
                recAdd("$par/${file.name}", f)
            }
        } else {
            Utils.sendQueue.offer(
                AppModal(
                    "...fol$par/${file.name}", "0", file, drDef!!
                )
            )
        }
    }

    private fun reqOverLay() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${packageName}".toUri()
        )
        startActivityForResult(intent, 101)
    }

    private fun getIp(): String {
        var str = "NOT"
        val man = getSystemService(WIFI_SERVICE) as WifiManager
        if (man.isWifiEnabled) {
            try {
                val soc = DatagramSocket()
                soc.connect(InetAddress.getByName("8.8.8.8"), 12345)
                str = soc.localAddress.hostAddress ?: "NOT"
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
                        str = add.hostAddress ?: "NOT"
                        if (!str.startsWith("192.168.") && !str.startsWith("10.")) str = "NOT"
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
            Utils.sendQueue.offer(
                AppModal(
                    f.name,
                    "0",
                    f,
                    ContextCompat.getDrawable(this, R.drawable.appfi)!!
                )
            )
            return
        }

        when (intent.type.toString()) {
            "video/*" -> {
                val path = uri.getVideoPath(applicationContext)
                if (path.first != "null" && File(path.first).canRead()) Utils.sendQueue.offer(
                    AppModal(
                        path.second,
                        "0",
                        File(path.first),
                        ContextCompat.getDrawable(this, R.drawable.appfi)!!
                    )
                )
                else handelTempFiles(uri)
            }

            "image/*" -> {
                val path = uri.getImagePath(applicationContext)
                if (path.first != "null" && File(path.first).canRead()) Utils.sendQueue.offer(
                    AppModal(
                        path.second,
                        "0",
                        File(path.first),
                        ContextCompat.getDrawable(this, R.drawable.appfi)!!
                    )
                )
                else handelTempFiles(uri)
            }

            "application/*" -> {
                val path = uri.getFilePath(applicationContext)
                if (path.first != "null" && File(path.first).canRead()) Utils.sendQueue.offer(
                    AppModal(
                        path.second,
                        "0",
                        File(path.first),
                        ContextCompat.getDrawable(this, R.drawable.appfi)!!
                    )
                )
                else handelTempFiles(uri)
            }

            "text/*", "null" -> {
                finish()
            }

            else -> {
                var path = uri.getFilePath(applicationContext)
                if (path.first != "null" && File(path.first).canRead()) Utils.sendQueue.offer(
                    AppModal(
                        path.second,
                        "0",
                        File(path.first),
                        ContextCompat.getDrawable(this, R.drawable.appfi)!!
                    )
                )
                else {
                    path = uri.getImagePath(applicationContext)
                    if (path.first != "null" && File(path.first).canRead()) Utils.sendQueue.offer(
                        AppModal(
                            path.second,
                            "0",
                            File(path.first),
                            ContextCompat.getDrawable(this, R.drawable.appfi)!!
                        )
                    )
                    else {
                        path = uri.getVideoPath(applicationContext)
                        if (path.first != "null" && File(path.first).canRead()) Utils.sendQueue.offer(
                            AppModal(
                                path.second,
                                "0",
                                File(path.first),
                                ContextCompat.getDrawable(this, R.drawable.appfi)!!
                            )
                        )
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
            if (f.length() > 0) Utils.sendQueue.offer(
                AppModal(
                    name,
                    "0",
                    f,
                    ContextCompat.getDrawable(this, R.drawable.appfi)!!
                )
            )
        } catch (_: Exception) {
        }

    }

//endregion File URI HANDLERS---->>

}
