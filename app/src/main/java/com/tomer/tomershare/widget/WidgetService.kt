package com.tomer.tomershare.widget

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.PointF
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.tomer.tomershare.R
import com.tomer.tomershare.databinding.WidgetLayBinding
import com.tomer.tomershare.utils.Utils
import com.tomer.tomershare.utils.Utils.Companion.rotate

class WidgetService : Service() {
    private val b by lazy { WidgetLayBinding.inflate(LayoutInflater.from(this)) }
    private val lay = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    private lateinit var windowManager: WindowManager

    private var canMove = false
    private var isDown = false
    private var isLeft = false

    private var initial = Point(0, 0)
    private var touch = PointF(0f, 0f)


    override fun onBind(p0: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (startId != 1) {

            if (intent.hasExtra("name")) {
                if (intent.getStringExtra("ext") == "fol") {
                    b.mainCard.setNae(intent.getStringExtra("name") ?: "File", R.drawable.ic_folder)
                    return START_NOT_STICKY
                }
                val icon = when (Utils.getHashMap()[intent.getStringExtra("ext")] ?: 4) {
                    0 -> R.drawable.ic_image
                    1 -> R.drawable.ic_android
                    2 -> R.drawable.ic_video
                    3 -> R.drawable.ic_pdf
                    5 -> R.drawable.ic_zip
                    6 -> R.drawable.txt_file
                    else -> R.drawable.file
                }

                b.mainCard.setNae(intent.getStringExtra("name") ?: "File", icon)
            } else if (intent.hasExtra("done")) {
                b.mainCard.visibility = View.GONE
                b.cardView.visibility = View.INVISIBLE
                b.imgRot.clearAnimation()
                val options = RequestOptions()
                b.imgRot.apply {
                    setPadding(0, 0, 0, 0)
                }
                try {
                    Glide.with(this)
                        .asGif()
                        .load(R.raw.done)
                        .apply(options)
                        .into(b.imgRot)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            } else {
                val prog = intent.getFloatExtra("prog", 0f)
                b.mainCard.setProg(prog)
                b.cardView.setProg(prog)
            }

            return START_NOT_STICKY
        }

        val imgParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            lay,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSPARENT
        )
        imgParams.gravity = Gravity.TOP
        imgParams.gravity = Gravity.END
        imgParams.y = 140

        //region CLICK AND SWAPPING -->>
        b.apply {
            imgRot.setOnTouchListener { _: View?, motionEvent: MotionEvent ->
                if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    isDown = true
                    b.root.postDelayed({
                        if (isDown) canMove = true
                    }, 120)
                    touch.x = motionEvent.rawX
                    touch.y = motionEvent.rawY
                    initial.x = imgParams.x
                    initial.y = imgParams.y


                } else if (motionEvent.action == MotionEvent.ACTION_MOVE) {
                    if (canMove) {
                        val x = motionEvent.rawX
                        val y = motionEvent.rawY
                        imgParams.x = initial.x + (touch.x - x).toInt()
                        imgParams.y = initial.y + (y - touch.y).toInt()
                        windowManager.updateViewLayout(b.root, imgParams)

                    }

                } else if (motionEvent.action == MotionEvent.ACTION_UP) {
                    if (!canMove) { // click conditoin here
                        if (b.mainCard.visibility != View.VISIBLE) b.mainCard.visibility = View.VISIBLE
                        else b.mainCard.visibility = View.GONE

                    }
                    val valueAnimator: ValueAnimator
                    if (motionEvent.rawX < windowManager.defaultDisplay.width / 2) {
                        valueAnimator = ValueAnimator.ofInt(
                            imgParams.x,
                            windowManager.defaultDisplay.width
                        )
                        isLeft = false
                    } else {
                        valueAnimator = ValueAnimator.ofInt(imgParams.x, 0)
                        isLeft = true
                    }
                    valueAnimator.duration = 142
                    valueAnimator.addUpdateListener { value: ValueAnimator ->
                        imgParams.x = value.animatedValue as Int
                        windowManager.updateViewLayout(b.root, imgParams)
                    }
                    valueAnimator.start()
                    canMove = false
                    isDown = false
                }

                true
            }
        }
        //endregion CLICK AND SWAPPING -->>

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager


        b.imgRot.rotate()


        try {
            windowManager.addView(b.root, imgParams)
        } catch (_: Exception) {
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(b.root)
    }
}