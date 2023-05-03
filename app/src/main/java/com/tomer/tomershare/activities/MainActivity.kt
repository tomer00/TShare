package com.tomer.tomershare.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.PointF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.tomer.tomershare.R
import com.tomer.tomershare.adap.AdaptApp
import com.tomer.tomershare.adap.AdaptFiles
import com.tomer.tomershare.adap.AdaptGal
import com.tomer.tomershare.databinding.ActivityMainBinding
import com.tomer.tomershare.databinding.DiaFileMenuBinding
import com.tomer.tomershare.modal.AppModal
import com.tomer.tomershare.modal.FileModal
import com.tomer.tomershare.modal.GalModal
import com.tomer.tomershare.utils.Repo
import com.tomer.tomershare.utils.Utils
import com.tomer.tomershare.utils.Utils.Companion.haptic
import com.tomer.tomershare.utils.Utils.Companion.px
import java.io.File
import java.io.IOException
import java.util.Stack
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(), AdaptApp.AppClickLis, AdaptGal.GalClickLis, AdaptFiles.FIleClickLis {

    //region GLOBALS

    private val b by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val repo by lazy { Repo(this.application) }


    private val mapFile by lazy { getHashMap() }
    private val listDrawable by lazy { getDrawables() }

    private val diaDelete by lazy { getDelDia() }


    private val adapApp by lazy { AdaptApp(this) }
    private val adapGal by lazy { AdaptGal(this, repo.getAllImagesVideos()) }
    private val adapFiles by lazy { AdaptFiles(this) }
    private val stack = Stack<File>()

    private val drDef by lazy { ContextCompat.getDrawable(this, R.drawable.logo) }

    private var mode = 1

    private var isGal = false
    private var isApp = false

    private val galPos = mutableListOf<Int>()
    private val appPositions = mutableListOf<Int>()

    private lateinit var tvDlName: TextView
    private var delFile: File = Environment.getExternalStorageDirectory()

    //endregion GLOBALS

    //region ACTIVITIES METHODS

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(b.root)

        b.apply {
            rvFiles.layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.VERTICAL, false)
            rvFiles.adapter = adapFiles

            btApp.setOnClickListener {
                if (mode == 0) {
                    return@setOnClickListener
                }
                if (!isApp) {
                    isApp = true
                    b.rvApps.layoutManager = GridLayoutManager(this@MainActivity, 4)
                    b.rvApps.adapter = adapApp
                    thread {
                        val ym = repo.getAllApps()
                        runOnUiThread {
                            adapApp.l.addAll(ym)
                            adapApp.notifyDataSetChanged()
                        }
                    }
                }
                mode = 0
                b.btApp.setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.appfi))
                b.btGal.setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.galout))
                b.btFile.setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.fileout))
                b.rvApps.visibility = View.VISIBLE
                b.rvGal.visibility = View.GONE
                b.rvFiles.visibility = View.GONE
                changeWidthHeightView(220)
            }
            createModals(Utils.allFiles(Environment.getExternalStorageDirectory()))

            btFile.setOnClickListener {
                if (mode == 1) {
                    return@setOnClickListener
                }
                mode = 1
                b.btApp.setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.appout))
                b.btGal.setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.galout))
                b.btFile.setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.filefil))
                b.rvApps.visibility = View.GONE
                b.rvGal.visibility = View.GONE
                b.rvFiles.visibility = View.VISIBLE
                changeWidthHeightView((46).px(resources.displayMetrics.density))
            }

            btGal.setOnClickListener {
                if (mode == 2) {
                    return@setOnClickListener
                }
                if (!isGal) {
                    isGal = true
                    thread {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            try {
                                runOnUiThread {
                                    b.rvGal.layoutManager = GridLayoutManager(this@MainActivity, 4)
                                    b.rvGal.adapter = adapGal
                                }
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                mode = 2
                b.btApp.setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.appout))
                b.btGal.setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.galfi))
                b.btFile.setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.fileout))
                b.rvApps.visibility = View.GONE
                b.rvGal.visibility = View.VISIBLE
                b.rvFiles.visibility = View.GONE
            }

            btRec.setOnClickListener {
                it.haptic()
                startActivity(Intent(this@MainActivity, ActivityReceiving::class.java))
                overridePendingTransition(
                    R.anim.enter_acti, R.anim.exit_d
                )
            }

            btSend.setOnClickListener {
                if (Utils.sendQueue.isNotEmpty()) {
                    startActivity(Intent(this@MainActivity, ActivitySending::class.java))
                    overridePendingTransition(
                        R.anim.enter_acti, R.anim.exit_d
                    )
                    if (isGal) {
                        galPos.forEach {
                            adapGal.l[it].visi = false
                            adapGal.notifyItemChanged(it)
                        }
                    }
                    if (isApp) {
                        appPositions.forEach {
                            adapApp.l[it].visi = 8
                            adapApp.notifyItemChanged(it)
                        }
                    }
                    val g: Byte = 8
                    for (i in 0 until adapFiles.currentList.size) if (adapFiles.currentList[i].visi != g) {
                        adapFiles.currentList[i].visi = 8
                        adapFiles.notifyItemChanged(i)
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Select at least 1 file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        b.tvItems.text = Utils.sendQueue.size.toString()
    }

    //endregion ACTIVITIES METHODS

    //region RECYCLER VIEW CLICKING

    override fun onAppClick(position: Int, indic: ImageView, thumb: ImageView) {
        val f: AppModal = adapApp.l[position]
        if (indic.visibility == View.GONE) {
            Utils.sendQueue.offer(AppModal(f.name + ".apk", "0", f.file, drDef!!))
            adapApp.l[position].visi = 0
            adapApp.notifyItemChanged(position)
            appPositions.add(position)
            playAnimationAddingFile(thumb)
        } else {
            val i: MutableIterator<AppModal> = Utils.sendQueue.iterator()
            var find: AppModal
            while (i.hasNext()) {
                find = i.next()
                if (f.file.absolutePath.equals(find.file.absolutePath)) {
                    i.remove()
                    adapApp.l[position].visi = 8
                    adapApp.notifyItemChanged(position)
                    appPositions.removeIf { it == position }
                    break
                }
            }
        }
        b.tvItems.text = Utils.sendQueue.size.toString()
    }

    override fun onGalClick(position: Int, img: ImageView) {
        val f: GalModal = adapGal.l[position]
        if (!f.visi) {
            img.scaleX = 0f
            img.scaleY = 0f
            img.animate().scaleX(1f).scaleY(1f).setInterpolator(OvershootInterpolator()).setDuration(240).start()
            Utils.sendQueue.offer(AppModal(f.file.name, "0", f.file, drDef!!))
            adapGal.l[position].visi = true
            galPos.add(position)
            b.tvItems.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).setInterpolator(OvershootInterpolator()).start()
            b.tvItems.postDelayed({ b.tvItems.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(OvershootInterpolator()).start() }, 200)
        } else {
            val i: MutableIterator<AppModal> = Utils.sendQueue.iterator()
            var find: AppModal
            while (i.hasNext()) {
                find = i.next()
                if (f.file.name.equals(find.file.name)) {
                    i.remove()
                    adapGal.l[position].visi = false
                    img.animate().scaleX(0f).scaleY(0f).setInterpolator(AccelerateInterpolator()).setDuration(120).start()
                    break
                }
            }
        }
        b.tvItems.text = Utils.sendQueue.size.toString()
    }

    override fun onFileClick(position: Int, indic: View, thumb: ImageView) {
        if (position == 0) {
            backDir()
            return
        }
        val f: File = try {
            adapFiles.currentList[position].file
        } catch (e: Exception) {
            return
        }
        if (f.isDirectory) {
            try {
                createModals(Utils.allFiles(f))
                stack.push(f)
            } catch (e: Exception) {
                Toast.makeText(this, "Not Allowed", Toast.LENGTH_SHORT).show()
            }

        } else {
            if (indic.visibility == View.GONE) {
                Utils.sendQueue.offer(AppModal(f.name, "0", f, drDef!!))
                changeFile(position, 0)
                indic.visibility = View.VISIBLE
                playAnimationAddingFile(thumb)
            } else {
                val i: MutableIterator<AppModal> = Utils.sendQueue.iterator()
                var find: AppModal
                while (i.hasNext()) {
                    find = i.next()
                    if (f.absolutePath == find.file.absolutePath) {
                        i.remove()
                        changeFile(position, 8)
                        indic.visibility = View.GONE
                        break
                    }
                }
            }
            b.tvItems.text = Utils.sendQueue.size.toString()
        }
    }

    override fun onFileLongClick(position: Int, indic: View, thumb: ImageView) {
        if (position == 0) return
        delFile = adapFiles.currentList[position].file
        if (delFile.isDirectory) {
            if (indic.visibility == View.GONE) {
                Utils.sendQueue.offer(AppModal(delFile.name, "0", delFile, drDef!!))
                changeFile(position, 0)
                indic.visibility = View.VISIBLE
                playAnimationAddingFile(thumb)
            } else {
                val i: MutableIterator<AppModal> = Utils.sendQueue.iterator()
                var find: AppModal
                while (i.hasNext()) {
                    find = i.next()
                    if (delFile.absolutePath == find.file.absolutePath) {
                        i.remove()
                        changeFile(position, 8)
                        indic.visibility = View.GONE
                        break
                    }
                }
            }
            b.tvItems.text = Utils.sendQueue.size.toString()
            return
        }
        diaDelete.show()
        "Really want to delete\n${delFile.name} ??".also { tvDlName.text = it }
    }

//endregion RECYCLER VIEW CLICKING

    @Deprecated("Updated in Future")
    override fun onBackPressed() {
        if (mode == 1) {
            if (stack.empty()) {
                Utils.sendQueue.clear()
                finishAffinity()
            }
            backDir()
        } else {
            Utils.sendQueue.clear()
            finishAffinity()
        }
    }

//region HELPER FUNCTIONS


    private fun changeFile(position: Int, vise: Byte) {
        val tm = mutableListOf<FileModal>()
        tm.addAll(adapFiles.currentList)
        tm[position].visi = vise
        adapFiles.submitList(tm)
    }

    private fun createModals(files: Array<File>) {
        val fModals = mutableListOf<FileModal>()
        val filed = mutableListOf<FileModal>()
        fModals.add(FileModal("..", Environment.getExternalStorageDirectory(), ContextCompat.getDrawable(this, R.drawable.ic_folder)!!))
        for (fl in files) {
            if (fl.isDirectory) {
                if (!fl.name.startsWith(".")) fModals.add(FileModal(fl.name, fl, ContextCompat.getDrawable(this, R.drawable.ic_folder)!!))
            } else filed.add(FileModal(fl.name, fl, listDrawable[mapFile[(fl.extension).lowercase()] ?: 4]))
        }
        fModals.sortBy { it.name }
        filed.sortBy { it.name }
        fModals.addAll(filed)
        adapFiles.submitList(fModals)
    }


    private fun backDir() {
        if (stack.empty()) return
        val fy: File = stack.pop().parentFile!!
        createModals(Utils.allFiles(fy))
    }


    private fun changeWidthHeightView(i: Int) {
        val l = b.imgTransfer.layoutParams
        l.height = i
        l.width = i
        b.imgTransfer.layoutParams = l
    }

    private fun aniTO1(p: PointF) {
        b.imgTransfer.apply {
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
            x = p.x - 12f
            y = p.y - 100f
        }

    }

    private fun animate0() {
        b.imgTransfer.animate().apply {
            scaleX(0.4f)
            scaleY(0.4f)
            alpha(0f)
            x(b.tvItems.x)
            y(b.tvItems.y)
            duration = 800
            start()
        }
    }

    private fun playAnimationAddingFile(thumb: ImageView) {
        b.imgTransfer.animation?.cancel()
        b.imgTransfer.clearAnimation()
        b.imgTransfer.setImageDrawable(thumb.drawable)
        val arr = IntArray(2)
        thumb.getLocationOnScreen(arr)
        aniTO1(PointF(arr[0].toFloat(), arr[1].toFloat()))
        b.imgTransfer.postDelayed({ animate0() }, 120)

        b.tvItems.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).setInterpolator(OvershootInterpolator()).start()
        b.tvItems.postDelayed({ b.tvItems.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(OvershootInterpolator()).start() }, 200)

    }

    private fun getDelDia(): AlertDialog {
        val fb: DiaFileMenuBinding = DiaFileMenuBinding.inflate(LayoutInflater.from(this))
        val b = AlertDialog.Builder(this)
        b.setView(fb.root)
        val finishD = b.create()
        finishD.window?.attributes?.windowAnimations = R.style.Dialog
        finishD.window?.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this, R.color.color_trans)))
        fb.dClose.setOnClickListener { finishD.cancel() }

        tvDlName = fb.dtvDetails
        fb.btDel.setOnClickListener {
            delFile.delete()
            diaDelete.dismiss()
            createModals(Utils.allFiles(delFile.parentFile!!))
        }
        return finishD
    }

//endregion HELPER FUNCTIONS

//region HASHMAP FOR DRAWABLE FOR DIFFERENT TYPES

    private fun getHashMap(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        map["jpg"] = 0
        map["jpeg"] = 0
        map["png"] = 0
        map["webp"] = 0
        map["gif"] = 0
        map["svg"] = 0
        map["bmp"] = 0
        map["ico"] = 0
        map["tif"] = 0
        map["psd"] = 0



        map["mp4"] = 2
        map["mkv"] = 2
        map["mov"] = 2
        map["wmv"] = 2
        map["flv"] = 2
        map["avi"] = 2
        map["webm"] = 2


        map["pdf"] = 3
        map["apk"] = 1


        map["zip"] = 5
        map["rar"] = 5
        map["tar"] = 5
        map["tar.gz"] = 5
        map["7z"] = 5

        return map
    }

    private fun getDrawables(): List<Drawable> {
        val dr = mutableListOf<Drawable>()
        ContextCompat.getDrawable(this, R.drawable.ic_image)?.let { dr.add(it) }
        ContextCompat.getDrawable(this, R.drawable.ic_android)?.let { dr.add(it) }
        ContextCompat.getDrawable(this, R.drawable.ic_video)?.let { dr.add(it) }
        ContextCompat.getDrawable(this, R.drawable.ic_pdf)?.let { dr.add(it) }
        ContextCompat.getDrawable(this, R.drawable.file)?.let { dr.add(it) }
        ContextCompat.getDrawable(this, R.drawable.ic_zip)?.let { dr.add(it) }

        return dr
    }

//endregion HASHMAP FOR DRAWABLE FOR DIFFERENT TYPES

}