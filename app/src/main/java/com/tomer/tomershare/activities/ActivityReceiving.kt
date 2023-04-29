package com.tomer.tomershare.activities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.ColorDrawable
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.CompoundBarcodeView
import com.tomer.tomershare.R
import com.tomer.tomershare.adap.AdaptMsg
import com.tomer.tomershare.databinding.ActivityRecivingBinding
import com.tomer.tomershare.databinding.BarcodeDiaBinding
import com.tomer.tomershare.databinding.RowNetBinding
import com.tomer.tomershare.modal.ModalNetwork
import com.tomer.tomershare.trans.RecHandler
import com.tomer.tomershare.utils.Repo
import com.tomer.tomershare.utils.RepoPref
import com.tomer.tomershare.utils.ShotCutCreator.Companion.createShotCut
import com.tomer.tomershare.utils.Utils
import com.tomer.tomershare.utils.Utils.Companion.bytesFromLong
import com.tomer.tomershare.utils.Utils.Companion.longFromBytearray
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import kotlin.concurrent.thread

class ActivityReceiving : AppCompatActivity() {

    //region GLOBALS--->>>
    private val b by lazy { ActivityRecivingBinding.inflate(layoutInflater) }
    private val adapSend by lazy { AdaptMsg(this, this::closeCurrFile) }


    private val networkList by lazy { mutableListOf<ModalNetwork>() }
    private val repo by lazy { RepoPref(applicationContext) }
    private lateinit var barcodeView: CompoundBarcodeView
    private val callback by lazy { callBack() }
    private val qrDia by lazy { crQr() }

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
    }

    //endregion GLOBALS--->>>

    //region ACTIVITY LIFECYCLES---->>>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(b.root)


        if (intent.getStringExtra("ip").toString() != "null")
            Utils.ADDRESS = intent.getStringExtra("ip").toString()
        else Utils.ADDRESS = repo.getLast().address

        repo.getAllNetwork().forEach { mod ->
            val row = RowNetBinding.inflate(layoutInflater)
            val netDr = if (mod.isWifi) R.drawable.ic_wifi
            else R.drawable.ic_hotspot
            row.imgNet.setImageDrawable(ContextCompat.getDrawable(this, netDr))
            row.imgIcon.setImageDrawable(ContextCompat.getDrawable(this, Repo.getMid(mod.icon)))

            row.root.tag = mod
            row.root.setOnClickListener(cliRvTop)

            b.rvTop.addView(row.root)
        }

        openNewConn()

    }


    override fun onDestroy() {
        super.onDestroy()
        stopped = true
        closeSockets()
    }


    // Init ui from initial to receiving
    private fun intiUI() {
        b.root.post {
            b.tRv.adapter = adapSend
            b.imgAvatarReceiver.apply {
                clearAnimation()
                rotation = 0f
                setImageDrawable(ContextCompat.getDrawable(this@ActivityReceiving, Repo.getMid(avatar)))
            }
            b.tvSendingName.text = "Receiving from $phoneName's Phone"
        }
    }

    //endregion ACTIVITY LIFECYCLES---->>>

    //region RECEIVING FILES---->>
    private fun closeCurrFile() {
        if (!transferGoing) return
        Log.d("TAG--", "closeCurrFile: Callde on line 193")
        soc!!.close()
    }


    private fun openNewConn() {
        thread {
            try {
                if (soc != null) soc!!.close()
                soc = Socket()
                soc!!.bind(null)
                soc!!.connect(InetSocketAddress(Utils.ADDRESS, Utils.SERVER_PORT))
                onOpen()
            } catch (e: Exception) {
                runOnUiThread {
                    if (!stopped && !transferGoing) {
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

    private fun onOpen() {
        if (!parentFolder.exists()) parentFolder.mkdirs()
        thread {
            val pref = getSharedPreferences("NAME", MODE_PRIVATE)
            soc!!.sendString("${pref.getString("name", "TShare")}::${pref.getString("icon", "1")}")
            time = SystemClock.elapsedRealtime()
            intiUI()
            reconnect()
        }

    }

    private fun onFinish() {
        if (!stopped) {
            runOnUiThread {
                Log.d("TAG--", "onSendingDone: 161...")
            }
        }

    }

    private fun reconnect() {
        runOnUiThread {
            thread {
                while (true) {
                    soc!!.connect(InetSocketAddress(Utils.ADDRESS, Utils.SERVER_PORT))
                    soc!!.getInputStream().read(bytesLong, 0, 8)
                    val size: Long = bytesLong.longFromBytearray()

                    if (size > 260) {
                        soc!!.close()
                        reconnect()
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
                    val f = File(parentFolder, fileReceiving)


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
                    onNewFIle(fileReceiving)
                    RecHandler(soc!!.getInputStream(), f, { long ->
                        finalTotalBytes += long
                        currentBytes += long
                        try {
                            val p = ((currentBytes * 100) / currTotalBytes).toFloat()
                            b.progTop.post { b.progTop.updateProg(p / 100) }
                        } catch (_: Exception) {
                        }
                    }, sizeReceiving)
                }
            }
        }
    }


    private fun onNewFIle(name: String) {
        runOnUiThread {
            b.apply {
                imgCenter.rotate()
                if (frLay.scaleX == 2f) {
                    val a = b.imgNoAnim.drawable as AnimationDrawable
                    a.stop()
                    b.imgNoAnim.visibility = View.GONE
                    b.tvNOConn.visibility = View.GONE

                    b.btShowQR.animate().apply {
                        scaleY(0f)
                        scaleX(0f)
                        translationYBy(200F)
                        duration = 600
                        withEndAction { b.btShowQR.visibility = View.GONE }
                        start()
                    }

                    frLay.animate().apply {
                        x(imgRotHelper.x)
                        y(imgRotHelper.y)
                        scaleY(0.8f)
                        scaleX(0.8f)
                        duration = 600
                        start()
                    }
                    progRec.animate().apply {
                        scaleX(1f)
                        duration = 600
                        start()
                    }
                    frLay.setOnClickListener(null)
                }
            }

            val list = mutableListOf<String>()
            list.addAll(adapMsg.currentList)
            list.add("$name,.,rec")
            adapMsg.submitList(list)
            b.tRv.smoothScrollToPosition(list.size - 1)
        }
    }


    private fun closeSockets() {
        try {
            if (soc != null) soc!!.close()
            if (serverSocket != null) serverSocket!!.close()
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
        fb.btCross.translationY = -120f
        fb.btCross.animate().translationY(0f).setDuration(600).setStartDelay(400).start()
        return qrd
    }

    private fun callBack() = BarcodeCallback { result ->
        barcodeView.pause()
        qrDia.dismiss()
        val sts = result?.text.toString().split(Pattern.compile("::"), 3)
        if (sts.size != 3) return@BarcodeCallback
        if (sts[1] == "NOT") {
            b.imgNoAnim.visibility = View.VISIBLE
            b.tvNOConn.visibility = View.VISIBLE
            val a = b.imgNoAnim.drawable as AnimationDrawable
            if (!a.isRunning)
                a.start()
            return@BarcodeCallback
        }
        saveIpAndOpenConnection(sts[1], sts[0], sts[2])
    }

    private fun saveIpAndOpenConnection(add: String, name: String, icon: String) {
        val man = getSystemService(Context.WIFI_SERVICE) as WifiManager
        val modalNetwork = ModalNetwork(add, name, man.isWifiEnabled, icon)
        var found = false
        networkList.forEach {
            if (it.address == modalNetwork.address) found = true
        }
        Utils.ADDRESS = modalNetwork.address
        onOpen()
        repo.saveLast(modalNetwork)
        if (found) return
        networkList.removeIf { it.name == modalNetwork.name && it.isWifi == modalNetwork.isWifi }
        networkList.add(modalNetwork)
        val shot = repo.getShortcut() as MutableList<ModalNetwork>
        this.createShotCut(modalNetwork, shot)
        repo.saveShotCuts(shot)
        repo.setAllNetwork(networkList)
    }


    private fun checkPermission(): Boolean {
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        return result == PackageManager.PERMISSION_GRANTED
    }

    //endregion BARCODE CALLBACK

}