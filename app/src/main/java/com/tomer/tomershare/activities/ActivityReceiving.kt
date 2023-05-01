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
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import kotlin.concurrent.thread

class ActivityReceiving : AppCompatActivity() {

    //region GLOBALS--->>>
    private val b by lazy { ActivityRecivingBinding.inflate(layoutInflater) }
    private val adaptSend by lazy { AdaptMsg(this, this::closeCurrFile) }


    private val networkList by lazy { mutableListOf<ModalNetwork>() }
    private val repo by lazy { RepoPref(applicationContext) }
    private lateinit var barcodeView: CompoundBarcodeView
    private val callback by lazy { callBack() }
    private val qrDia by lazy { crQr() }

    private var soc: Socket? = null
    private val bytesLong = ByteArray(8)

    private var time: Long = 0
    private var timeCurrent: Long = 0


    @Volatile
    private var currTotalBytes = 0L
    @Volatile
    private var currentBytes = 0L
    @Volatile
    private var finalTotalBytes = 0L
    @Volatile
    private var transferGoing = false
    private var stopped = false


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

        repo.getAllNetwork().forEach { mod ->
            val view = layoutInflater.inflate(R.layout.row_net, b.rvTop, false)
            val row = RowNetBinding.bind(view)
            val netDr = if (mod.isWifi) R.drawable.ic_wifi
            else R.drawable.ic_hotspot
            row.imgNet.setImageDrawable(ContextCompat.getDrawable(this, netDr))
            row.imgIcon.setImageDrawable(ContextCompat.getDrawable(this, Repo.getMid(mod.icon)))

            row.root.tag = mod
            row.root.setOnClickListener(cliRvTop)

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

        b.imgAvatarReceiver.rotate()
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
            b.tRv.adapter = adaptSend
            b.imgAvatarReceiver.apply {
                clearAnimation()
                rotation = 0f
                setImageDrawable(ContextCompat.getDrawable(this@ActivityReceiving, Repo.getMid(avatar)))
            }
            "Receiving from $phoneName's Phone".also { b.tvSendingName.text = it }
            b.btShowQR.visibility = View.GONE
            b.rvTop.visibility = View.GONE
            b.imgNoAnim.visibility = View.GONE
        }
    }

    //endregion ACTIVITY LIFECYCLES---->>>

    //region RECEIVING FILES---->>
    private fun closeCurrFile() {
        if (!transferGoing) return
        soc!!.close()
    }


    private fun openNewConn() {
        runOnUiThread {
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

    private fun onFinish() {
        if (!stopped) {
            runOnUiThread {
                b.apply {
                    progTop.visibility = View.GONE
                    finishView.visibility = View.VISIBLE
                    val li = mutableListOf<TransferModal>()
                    adaptSend.currentList.forEach { mod ->
                        li.add(TransferModal(mod.fileName))
                    }
                    adaptSend.submitList(li)
                }
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

                        if (size > 260) {
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
                        val f = File(parentFolder, fileReceiving)


                        b.progTop.post { b.progTop.updateProg(0f) }
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
                        transferGoing = true
                        RecHandler(soc!!, f, { long ->
                            finalTotalBytes += long
                            currentBytes += long
                            try {
                                val p = ((currentBytes * 100) / currTotalBytes).toFloat()
                                b.progTop.post { b.progTop.updateProg(p / 100) }
                            } catch (_: Exception) {
                            }
                        }, sizeReceiving)
                        transferGoing = false

                        if (soc!!.isClosed) {
                            reListen()
                            return@thread
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
//        val sts = result?.text.toString().split(Pattern.compile("::"), 3)
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
        networkList.forEach {
            if (it.address == modalNetwork.address) found = true
        }
        Utils.ADDRESS = modalNetwork.address
        openNewConn()
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