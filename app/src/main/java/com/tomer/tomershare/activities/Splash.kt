package com.tomer.tomershare.activities

import android.Manifest.permission.*
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.HorizontalScrollView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tomer.tomershare.R
import com.tomer.tomershare.databinding.ActivitySplashBinding
import com.tomer.tomershare.databinding.DiaNameBinding
import com.tomer.tomershare.utils.Utils.Companion.haptic
import com.tomer.tomershare.utils.Utils.Companion.rotate


@Suppress("DEPRECATION")
class Splash : AppCompatActivity() {

    private val b by lazy { ActivitySplashBinding.inflate(layoutInflater) }
    private val networkDia by lazy { crNetwork() }
    private var icon = "1"

    //region ACTIVITY METHODS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(b.root)

        val bg = b.root.background!! as AnimationDrawable
        bg.setEnterFadeDuration(2000)
        bg.setExitFadeDuration(4000)
        bg.start()

        b.apply {
            btRec.setOnClickListener {
                btRec.haptic()
                startActivity(Intent(this@Splash, ActivityReceiving::class.java))
                overridePendingTransition(android.R.anim.fade_in, R.anim.exit_rec)
                root.postDelayed({ finish() }, 800)
            }
            btSend.setOnClickListener {
                btSend.haptic()
                startActivity(Intent(this@Splash, MainActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, R.anim.exit_scr)
                btSendBG.animate().apply {
                    scaleX(4f)
                    scaleY(4f)
                    duration = 400
                    start()
                }
                root.postDelayed({ finish() }, 800)
            }
            aniM(btRec, 400)
            aniM(btSend, 200)
            aniM(imgCenter, 600)

            imgCenter.rotate()
        }

        val pref = getSharedPreferences("NAME", MODE_PRIVATE)
        val name = pref.getString("name", "__")
        val icon = pref.getString("icon", "-1")
        if (name == "__" || icon == "-1")
            networkDia.show()

        if (!checkPermission()) {
            if (SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                    startActivityForResult(intent, 2296)
                } catch (e: Exception) {
                    val intent = Intent().apply {
                        action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    }
                    startActivityForResult(intent, 2296)
                }
            } else {
                //below android 11
                ActivityCompat.requestPermissions(this, arrayOf(WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE, CAMERA), 100)
            }
        }
    }

    private fun crNetwork(): AlertDialog {

        val fb: DiaNameBinding = DiaNameBinding.inflate(LayoutInflater.from(this))
        val bul = AlertDialog.Builder(this)
        bul.setView(fb.root)
        val finishD = bul.create()
        finishD.window?.attributes?.windowAnimations = R.style.Dialog
        finishD.window?.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this, R.color.color_trans)))
        fb.etAdd.setText(Build.MODEL.toString())

        fb.btSave.setOnClickListener {
            if (fb.etAdd.text.isNullOrEmpty()) {
                fb.etAdd.error = "Your Name"
                return@setOnClickListener
            }
            getSharedPreferences("NAME", MODE_PRIVATE).edit().putString("name", fb.etAdd.text.toString()).putString("icon", icon).apply()
            finishD.cancel()
        }
        finishD.setCancelable(false)
        finishD.setCanceledOnTouchOutside(false)
        return finishD
    }

    private fun checkPermission(): Boolean {
        return if (SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val result = ContextCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE)
            val result1 = ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE)
            result == PackageManager.PERMISSION_GRANTED && result1 == PackageManager.PERMISSION_GRANTED
        }
    }

    //endregion ACTIVITY METHODS

    fun avr(v: View) {
        icon = v.tag as String
        try {
            networkDia.findViewById<HorizontalScrollView>(R.id.scrl)!!.visibility = View.GONE
        } catch (_: Exception) {
        }
    }

    private fun aniM(v: View, isD: Long) {
        v.animate().apply {
            scaleX(1f)
            scaleY(1f)
            duration = 320
            startDelay = isD
            interpolator = OvershootInterpolator(2f)
            start()
        }
    }

    //region FULLSCREEN LOGIC
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        }
    }
    //endregion FULLSCREEN LOGIC
}