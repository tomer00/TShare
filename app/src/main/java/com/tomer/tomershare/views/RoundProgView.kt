package com.tomer.tomershare.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class RoundProgView : View {

    //region :::CONSTRUCTOR---->>
    constructor(con: Context) : super(con)
    constructor(con: Context, attrs: AttributeSet) : super(con, attrs)
    constructor(con: Context, attrs: AttributeSet, def: Int) : super(con, attrs, def)


    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(measuredWidth), MeasureSpec.getSize(measuredHeight))
        size.set(16f, 16f, MeasureSpec.getSize(measuredWidth).toFloat() - 16, MeasureSpec.getSize(measuredHeight).toFloat() - 16)
    }

    //endregion CONSTRUCTOR---->>

    //region ::GLOBALS--->>>

    private val size = RectF()

    private val colorLight = Color.parseColor("#D2EDFC")
    private val colorProg = Color.parseColor("#3DDC84")

    private val paint = Paint().apply {
        color = colorLight
        isAntiAlias = true
    }

    private val arcPaint = Paint().apply {
        color = colorProg
        isAntiAlias = true
        strokeWidth = 16f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var prog = 0f //from 0 to 1

    private val animator = ValueAnimator().apply {
        duration = 400
        this.addUpdateListener {
            postInvalidate()
            prog = animatedValue as Float
        }
    }

    //endregion ::GLOBALS--->>>

    //region ::DRAWING-->>

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 1000f, 1000f, paint) // circle bg
        canvas.drawArc(size, 270f, prog * 360, false, arcPaint)
    }

    //endregion ::DRAWING-->>

    //region ::COMMUNICATION---->>>

    fun setProg(progress: Float) {
        if (animator.isRunning) animator.end()
        if (progress==prog) return
        animator.setFloatValues(prog, progress)
        animator.start()
    }

    //endregion ::COMMUNICATION---->>>
}