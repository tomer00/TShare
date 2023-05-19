package com.tomer.tomershare.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.tomer.tomershare.utils.Utils.Companion.toPx

class ProgressView : View {

    //region :::CONSTRUCTOR---->>
    constructor(con: Context) : super(con)
    constructor(con: Context, attrs: AttributeSet) : super(con, attrs)
    constructor(con: Context, attrs: AttributeSet, def: Int) : super(con, attrs, def)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        dimen.set(w, h)
        per20 = w / 5f

        val paintArc = Paint().apply {
            style = Paint.Style.FILL
        }
        val shader = LinearGradient(0f, 0f, per20, 0f, coloLight, colEnd, Shader.TileMode.CLAMP)
        bitMap = Bitmap.createBitmap(w / 5, h, Bitmap.Config.ARGB_8888)
        paintArc.shader = shader
        val c = Canvas(bitMap)
        c.drawRoundRect(0f, 0f, bitMap.width.toFloat(), h.toFloat()-20.toPx(), 14.toPx(), 14.toPx(), paintArc)
    }

//    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
//        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
//        Log.d("TAG--", "onMeasure: widthMeasureSpec = $widthMeasureSpec, heightMeasureSpec = $heightMeasureSpec")
//        setMeasuredDimension(MeasureSpec.getMode(widthMeasureSpec),120)
//    }

    //endregion CONSTRUCTOR---->>

    //region :::GLOBALS---->>

    private var prog = 0f
    private var per20 = 0f
    private val dimen = Point(0, 0)
    private lateinit var bitMap: Bitmap

    private val coloLight = Color.parseColor("#D2EDFC")
    private val colEnd = Color.parseColor("#3DDC84")


    private val animator = ValueAnimator().apply {
        duration = 400
        this.addUpdateListener {
            prog = animatedValue as Float
            postInvalidate()
        }
    }

    //endregion :::GLOBALS---->>

    //region :::DRAWING-->>

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(coloLight)
        canvas.drawBitmap(bitMap, 10.toPx() + (prog * (width - 20.toPx())) - per20, 10.toPx(), null)
    }

    //endregion :::DRAWING-->>

    //region COMMUNICATIONS---->>

    fun updateProg(progFloat: Float) {
        if (animator.isRunning) animator.end()
        if (prog == progFloat) return
        animator.setFloatValues(prog, progFloat)
        animator.start()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == 8) animator.end()
    }
    //endregion COMMUNICATIONS---->>
}