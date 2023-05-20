package com.tomer.tomershare.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.VectorDrawable
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.tomer.tomershare.R

class WidgetView : View {

    //region :::CONSTRUCTOR---->>
    constructor(con: Context) : super(con)
    constructor(con: Context, attrs: AttributeSet) : super(con, attrs)
    constructor(con: Context, attrs: AttributeSet, def: Int) : super(con, attrs, def)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(800, 352)
    }

    //endregion CONSTRUCTOR---->>

    //region ::GLOBALS---->>

    private val img = BitmapFactory.decodeResource(resources, R.drawable.widget)
    private val bmpRect = Rect(0, 0, img.width, img.height)
    private val canvasRect = Rect(0, 0, 800, 352)

    private val colorLight = Color.parseColor("#D2EDFC")
    private val colorProg = Color.parseColor("#3DDC84")

    private val paint = Paint().apply {
        color = colorLight
    }
    private val paintP = Paint().apply {
        color = colorProg
        isAntiAlias = true
    }
    private val paintTextInner = TextPaint().apply {
        typeface = ResourcesCompat.getFont(context, R.font.quantico_bold_italic)
        textSize = 42f
        this.color = ContextCompat.getColor(context, R.color.white)
        isAntiAlias = true
    }
    private var prog = 0f //from 0 to 1
    private var line1 = ""
    private var line2 = ""
    private val maxTextWidth = 680f

    private val animator = ValueAnimator().apply {
        duration = 400
        this.addUpdateListener {
            postInvalidate()
            prog = animatedValue as Float
        }
    }

    private var bmpFileType: Bitmap = getBmp(R.drawable.file)

    //endregion ::GLOBALS---->>

    //region ::DRAWING------->>>

    override fun onDraw(canvas: Canvas) {

        canvas.drawRect(60f, 55f, 650f, 138f, paint)
        canvas.drawRect(654f, 55f, 735f, 138f, paint)
        canvas.drawRoundRect(-16f, 57.6f, 80 + (prog * 578), 137.6f, 80f, 80f, paintP) // progress
        canvas.drawBitmap(img, bmpRect, canvasRect, null)

        canvas.drawText(line1, 60f, 240f, paintTextInner)
        canvas.drawText(line2, 60f, 300f, paintTextInner)

        canvas.drawBitmap(bmpFileType, 664.5f, 67.6f, null)
    }

    //endregion ::DRAWING------->>>

    private fun getBmp(id: Int): Bitmap {
        val bmp = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val dr = (ContextCompat.getDrawable(context, id))
        if (dr is VectorDrawable) {
            dr.setBounds(0, 0, 60, 60)
            dr.draw(c)
        } else {
            val b = BitmapFactory.decodeResource(resources, id)
            c.drawBitmap(b, Rect(0, 0, b.width, b.height), RectF(0f, 0f, 60f, 60f), null)
        }
        return bmp
    }

    //region ::COMMUNICATION---->>>

    fun setProg(progress: Float) {
        if (animator.isRunning) animator.end()
        if (prog == progress) return
        animator.setFloatValues(prog, progress)
        animator.start()
    }

    fun setNae(name: String, icon: Int) {
        if (paintTextInner.measureText(name) > maxTextWidth) {
            var i1 = 0
            for (i in name.indices) {
                if (paintTextInner.measureText(name, 0, i) > maxTextWidth) {
                    i1 = i - 1
                    break
                }
            }
            line1 = name.substring(0, i1)
            var i2 = -1
            for (i in i1 until name.lastIndex) {
                if (paintTextInner.measureText(name, i1, i) > maxTextWidth) {
                    i2 = i
                    break
                }
            }
            line2 = if (i2 == -1) {
                name.substring(i1, name.length)
            } else name.substring(i1, i2 - 1)
        } else {
            line1 = name
            line2 = ""
        }
        bmpFileType = getBmp(icon)
        postInvalidate()
    }
    //endregion ::COMMUNICATION---->>>
}