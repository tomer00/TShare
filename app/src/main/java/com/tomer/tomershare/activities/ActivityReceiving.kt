package com.tomer.tomershare.activities

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.CompoundBarcodeView
import com.tomer.tomershare.R
import com.tomer.tomershare.adap.AdaptMsg
import com.tomer.tomershare.databinding.ActivityRecivingBinding
import com.tomer.tomershare.databinding.BarcodeDiaBinding
import com.tomer.tomershare.databinding.RowNetBinding
import com.tomer.tomershare.modal.ModalNetwork
import com.tomer.tomershare.modal.TransferModal
import com.tomer.tomershare.trans.RecHandler
import com.tomer.tomershare.utils.CipherUtils
import com.tomer.tomershare.utils.Repo
import com.tomer.tomershare.utils.RepoPref
import com.tomer.tomershare.utils.ShotCutCreator.Companion.createShotCut
import com.tomer.tomershare.utils.Utils
import com.tomer.tomershare.utils.Utils.Companion.bytesFromLong
import com.tomer.tomershare.utils.Utils.Companion.longFromBytearray
import com.tomer.tomershare.utils.Utils.Companion.rotate
import com.tomer.tomershare.views.EndPosterProvider.Companion.getEndPoster
import com.tomer.tomershare.widget.WidgetService
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import kotlin.concurrent.thread

class ActivityReceiving : AppCompatActivity() {

    //region GLOBALS--->>>
    private val b by lazy { ActivityRecivingBinding.inflate(layoutInflater) }
    private val adaptSend by lazy { AdaptMsg(this, this::onClickRvItem) }

    private val repo by lazy { RepoPref(applicationContext) }
    private lateinit var barcodeView: CompoundBarcodeView
    private val callback by lazy { callBack() }
    private val qrDia by lazy { crQr() }

    private var soc: Socket? = null
    private val bytesLong = ByteArray(8)

    private var time: Long = 0

    @Volatile
    private var currTotalBytes = 0L

    @Volatile
    private var currentBytes = 0L

    @Volatile
    private var finalTotalBytes = 0L

    @Volatile
    private var transferGoing = false
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

    private var avatar = "1"
    private var phoneName = "Android"

    private val parentFolder = File(Environment.getExternalStorageDirectory(), "tshare")


    @Throws(Exception::class)
    private fun Socket.sendString(string: String) {
        val strata: ByteArray = string.toByteArray(StandardCharsets.UTF_8)
        this.getOutputStream().write(strata.size.toLong().bytesFromLong())
        this.getOutputStream().write(strata)
        this.getOutputStream().flush()
    }

    private val cliRvTop = View.OnClickListener { v ->
        val mod = v.tag as ModalNetwork
        Utils.ADDRESS = mod.address
        avatar = mod.icon
        phoneName = mod.name
        openNewConn()
        repo.saveLast(mod)
    }

    private val longcliRvtop = View.OnLongClickListener { v ->
        val mod = v.tag as ModalNetwork
        val list = repo.getAllNetwork().toMutableList()
        list.remove(mod)
        repo.setAllNetwork(list)
        b.rvTop.removeView(v)
        return@OnLongClickListener true
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

    //endregion GLOBALS--->>>

    //region ACTIVITY LIFECYCLES---->>>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(b.root)

        if (intent.getStringExtra("ip").toString() != "null") {
            Utils.ADDRESS = intent.getStringExtra("ip").toString()
            phoneName = intent.getStringExtra("name").toString()
            avatar = intent.getStringExtra("icon").toString()
        } else {
            val mod = repo.getLast()
            Utils.ADDRESS = mod.address
            phoneName = mod.name
            avatar = mod.icon
        }

        repo.getAllNetwork().reversed().forEach { mod ->
            val view = layoutInflater.inflate(R.layout.row_net, b.rvTop, false)
            val row = RowNetBinding.bind(view)
            val netDr = if (mod.isWifi) R.drawable.ic_wifi
            else R.drawable.ic_hotspot
            row.imgNet.setImageDrawable(ContextCompat.getDrawable(this, netDr))
            row.imgIcon.setImageDrawable(ContextCompat.getDrawable(this, Repo.getMid(mod.icon)))
            row.tvName.text = mod.name

            row.root.tag = mod
            row.root.setOnClickListener(cliRvTop)
            row.root.setOnLongClickListener(longcliRvtop)

            b.rvTop.addView(row.root)
        }
        b.btShowQR.setOnClickListener {
            if (!checkPermission()) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
            } else {
                qrDia.show()
                barcodeView.resume()
                barcodeView.decodeSingle(callback)
            }
        }
        "Connecting with $phoneName...".also { b.tvSendingName.text = it }
        b.imgAvatarReceiver.rotate()
        openNewConn()
        if (!Settings.canDrawOverlays(this)) reqOverLay()
    }

    override fun onResume() {
        super.onResume()
        stopService(serIntent)
        isService = false
    }

    override fun onPause() {
        super.onPause()
        if (transferGoing && !isFinishing) {
            serIntent.putExtra("send", false)
            startService(serIntent)
            serIntent.removeExtra("send")
            val name = adaptSend.currentList.last().fileName
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
        closeSockets()
    }

    // Finish ui
    private fun onFinish() {
        isMetricThread = false
        if (!stopped) {
            runOnUiThread {
                b.root.keepScreenOn = false
                b.apply {
                    val timeTaken = SystemClock.elapsedRealtime() - time
                    progTop.visibility = View.GONE

                    if (isService) {
                        serIntent.removeExtra("name")
                        serIntent.putExtra("done", true)
                        startService(serIntent)
                        isService = false
                    }

                    val volume = Utils.humanReadableSize(finalTotalBytes)
                    try {
                        val speed = String.format("%1$.2f", (finalTotalBytes / 1048576f) / (timeTaken / 1000f))
                        finishView.setImageBitmap(this@ActivityReceiving.getEndPoster(speed, volume))
                    } catch (_: Exception) {
                        finishView.setImageBitmap(this@ActivityReceiving.getEndPoster("0.00", volume))
                    }
                    finishView.visibility = View.VISIBLE

                    val tme = (timeTaken / 1000).toInt()
                    val str = if (tme < 60) "Received in just $tme seconds..."
                    else "Received in just ${tme / 60} min and ${tme % 60} seconds..."
                    tvSendingName.text = str
                    imgAvatarReceiver.setImageDrawable(ContextCompat.getDrawable(this@ActivityReceiving, R.drawable.ic_clock))
                    val li = mutableListOf<TransferModal>()
                    adaptSend.currentList.forEach { mod ->
                        li.add(TransferModal(mod.fileName))
                    }
                    adaptSend.submitList(li)
                }
            }
        }
    }

    // Init ui from initial to receiving
    private fun intiUI() {
        b.root.post {
            b.tRv.adapter = adaptSend
            b.imgAvatarReceiver.apply {
                clearAnimation()
                rotation = 0f
                setImageDrawable(ContextCompat.getDrawable(this@ActivityReceiving, Repo.getMid(avatar)))
            }
            "Receiving from $phoneName".also { b.tvSendingName.text = it }
            b.btShowQR.visibility = View.GONE
            b.rvTop.visibility = View.GONE
            b.imgNoAnim.visibility = View.GONE
            b.tvNOConn.visibility = View.GONE

            b.layMetrics.visibility = View.VISIBLE
        }
        isMetricThread = true
        metricThread.start()
    }

    private fun reqOverLay() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${packageName}")
        )
        startActivityForResult(intent, 101)
    }

    //endregion ACTIVITY LIFECYCLES---->>>

    //region RECEIVING FILES---->>
    private fun onClickRvItem(isClose: Boolean, pos: Int) {
        if (isClose) {
            if (!transferGoing) return
            soc!!.close()
            return
        }

//        Log.d("TAG--", "onClickRvItem: $pos")
//
//        val folder = File(parentFolder,"")
//
//        val int = Intent(Intent.ACTION_VIEW).apply {
//            setDataAndType(FileProvider.getUriForFile(this@ActivityReceiving, "com.tomer.tomershare.provider", folder),"*/*")
//            flags = Intent.FLAG_ACTIVITY_NEW_TASK
//        }
//        try {
//            startActivity(int)
//        }catch (e:Exception){
//            e.printStackTrace()
//            Log.e("TAG--", "onClickRvItem: ",e)
//        }
    }


    private fun openNewConn() {
        runOnUiThread {
            b.root.postDelayed({
                if (soc!!.isConnected) return@postDelayed
                if (soc != null) soc!!.close()
            }, 1000)
            thread {
                try {
                    if (soc != null) soc!!.close()
                    soc = Socket()
                    soc!!.bind(null)
                    soc!!.connect(InetSocketAddress(Utils.ADDRESS, Utils.SERVER_PORT))
                    onOpen()
                } catch (_: Exception) {
                    runOnUiThread {
                        if (!stopped && !transferGoing) {
                            "Failed to connect...".also { b.tvSendingName.text = it }
                            b.btShowQR.visibility = View.VISIBLE
                            b.btShowQR.animate().apply {
                                scaleY(1f)
                                scaleX(1f)
                                duration = 400
                                interpolator = OvershootInterpolator(1.2f)
                                start()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onOpen() {
        if (!parentFolder.exists()) parentFolder.mkdirs()
        runOnUiThread {
            thread {
                val pref = getSharedPreferences("NAME", MODE_PRIVATE)
                try {
                    soc!!.sendString("${pref.getString("name", "TShare")}${pref.getString("icon", "1")}")
                } catch (_: Exception) {
                }
                time = SystemClock.elapsedRealtime()
                intiUI()
                recData()
            }
        }
    }

    private fun reListen() {
        try {
            if (soc != null) soc!!.close()
            if (!stopped) {
                soc = Socket()
                soc!!.bind(null)
                SystemClock.sleep(10)
                soc!!.connect(InetSocketAddress(Utils.ADDRESS, Utils.SERVER_PORT))
                recData()
            }
        } catch (e: Exception) {
            onFinish()
        }
    }

    private fun recData() {
        runOnUiThread {
            thread {
                while (true) {
                    try {
                        soc!!.getInputStream().read(bytesLong, 0, 8)
                        val size: Long = bytesLong.longFromBytearray()

                        if (size > 1200) {
                            soc!!.close()
                            reListen()
                            break
                        }

                        val nameBytes = ByteArray(size.toInt())
                        soc!!.getInputStream().read(nameBytes)

                        // name of file currently receiving.......
                        val fileReceiving = String(nameBytes, StandardCharsets.UTF_8)

                        if (fileReceiving == "FINISH") {
                            onFinish()
                            break
                        }

                        currentBytes = 0
                        currTotalBytes = 0

                        val f = if (fileReceiving.startsWith("...fol")) {
                            File(parentFolder.absolutePath + fileReceiving.substring(6)).also {
                                if (it.parentFile?.exists() == false) it.parentFile?.mkdirs()
                            }
                        } else File(parentFolder, fileReceiving)

                        // sending cursor for this file
                        if (f.exists()) {
                            soc!!.getOutputStream().write(f.length().bytesFromLong())
                            currTotalBytes = f.length()
                            currentBytes = f.length()
                        } else soc!!.getOutputStream().write((0L).bytesFromLong())


                        // receiving the length remaining that we will be receiving...
                        soc!!.getInputStream().read(bytesLong, 0, 8)
                        val sizeReceiving = bytesLong.longFromBytearray()

                        currTotalBytes += sizeReceiving

                        b.root.post {
                            val p = try {
                                ((currentBytes * 100) / currTotalBytes).toFloat()
                            } catch (_: Exception) {
                                0f
                            }
                            b.progTop.updateProg(p / 100)
                            if (isService) {
                                serIntent.putExtra("name", fileReceiving)
                                serIntent.putExtra("ext", f.extension)
                                startService(serIntent)
                                serIntent.removeExtra("name")
                                serIntent.putExtra("prog", p / 100)
                                startService(serIntent)
                            }
                        }
                        timer.start()
                        onNewFIle(fileReceiving)
                        transferGoing = true
                        RecHandler(soc!!, f, { long ->
                            finalTotalBytes += long
                            currentBytes += long
                        }, sizeReceiving)
                        transferGoing = false
                        timer.cancel()
                        if (soc!!.isClosed) {
                            reListen()
                            return@thread
                        } else runOnUiThread {
                            addContentValue(f)
                        }
                    } catch (_: Exception) {
                        reListen()
                        break
                    }
                }
            }
        }
    }


    private fun onNewFIle(name: String) {
        runOnUiThread {
            val list = mutableListOf<TransferModal>()
            list.addAll(adaptSend.currentList)
            list.add(TransferModal(name, true))
            adaptSend.submitList(list)
            try {
                adaptSend.currentList[list.size - 2].isTrans = false
                adaptSend.notifyItemChanged(list.size - 2)
            } catch (_: Exception) {
            }
            b.tRv.smoothScrollToPosition(list.size - 1)
        }
    }

    private fun addContentValue(file: File) {
        if (Utils.getHashMap()[file.extension] == 0) {
            val cn = ContentValues().apply {
                put(MediaStore.Images.Media.MIME_TYPE, "image/${file.extension}")
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.DATA, file.absolutePath)
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cn)
        } else if (Utils.getHashMap()[file.extension] == 2) {
            val cn = ContentValues().apply {
                put(MediaStore.Video.Media.MIME_TYPE, "video/${file.extension}")
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.DATA, file.absolutePath)
            }
            contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cn)
        }
    }

    private fun closeSockets() {
        try {
            if (soc != null) soc!!.close()
        } catch (_: Exception) {
        }
    }

    //endregion RECEIVING FILES---->>

    //region BARCODE CALLBACK

    private fun crQr(): AlertDialog {
        val fb: BarcodeDiaBinding = BarcodeDiaBinding.inflate(LayoutInflater.from(this))
        val b = AlertDialog.Builder(this)
        b.setView(fb.root)
        val qrd = b.create()
        qrd.window?.attributes?.windowAnimations = R.style.Dialog
        qrd.window?.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this, R.color.color_trans)))
        fb.btCross.setOnClickListener {
            qrd.cancel()
        }
        fb.barcodeView.setStatusText("")
        barcodeView = fb.barcodeView
        fb.btCross.translationY = 120f
        fb.btCross.animate().translationY(0f).setDuration(400).setStartDelay(400).start()

        qrd.setOnCancelListener {
            barcodeView.pause()
        }

        return qrd
    }

    private fun callBack() = BarcodeCallback { result ->
        barcodeView.pause()
        qrDia.dismiss()
        val sts = CipherUtils.performString(result?.text.toString()).split(Pattern.compile("::"), 3)
        if (sts.size != 3) return@BarcodeCallback
        if (sts[1] == "NOT") {
            b.imgNoAnim.visibility = View.VISIBLE
            b.tvNOConn.visibility = View.VISIBLE
            val a = b.imgNoAnim.drawable as AnimationDrawable
            if (!a.isRunning)
                a.start()
            return@BarcodeCallback
        }
        avatar = sts[2]
        phoneName = sts[0]
        saveIpAndOpenConnection(sts[1], sts[0], sts[2])
    }

    private fun saveIpAndOpenConnection(add: String, name: String, icon: String) {
        val man = getSystemService(Context.WIFI_SERVICE) as WifiManager
        val modalNetwork = ModalNetwork(add, name, man.isWifiEnabled, icon)
        var found = false
        val networkList = repo.getAllNetwork().toMutableList()

        val itr = networkList.iterator()
        while (itr.hasNext()) {
            val mod = itr.next()
            if (mod.address == modalNetwork.address) found = true
            if (mod.name == modalNetwork.name)
                itr.remove()
        }

        Utils.ADDRESS = modalNetwork.address
        openNewConn()
        repo.saveLast(modalNetwork)
        if (found) return
        networkList.add(modalNetwork)
        repo.setAllNetwork(networkList)
        val shot = repo.getShortcut() as MutableList<ModalNetwork>
        this.createShotCut(modalNetwork, shot)
        repo.saveShotCuts(shot)
    }


    private fun checkPermission(): Boolean {
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        return result == PackageManager.PERMISSION_GRANTED
    }

    //endregion BARCODE CALLBACK

}