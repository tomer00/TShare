package com.tomer.tomershare.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Shader
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import com.tomer.tomershare.R

class ProgressView : View {

    //region :::CONSTRUCTOR---->>
    constructor(con: Context) : super(con)
    constructor(con: Context, attrs: AttributeSet) : super(con, attrs)
    constructor(con: Context, attrs: AttributeSet, def: Int) : super(con, attrs, def)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        dimen.set(w, h)
        shader = LinearGradient(0f, 0f, w.toFloat(), 0f, coloLight, colEnd, Shader.TileMode.CLAMP)
        paintArc.shader = shader
    }

//    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
//        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
//        Log.d("TAG--", "onMeasure: widthMeasureSpec = $widthMeasureSpec, heightMeasureSpec = $heightMeasureSpec")
//        setMeasuredDimension(MeasureSpec.getMode(widthMeasureSpec),120)
//    }

    //endregion CONSTRUCTOR---->>

    //region :::GLOBALS---->>

    private var prog = 0f
    private val dimen = Point(0, 0)

    private val coloLight = Color.parseColor("#D2EDFC")
    private val colEnd = Color.parseColor("#FFFF1744")
    private lateinit var shader: Shader
    private val paintArc = Paint().apply {
        color = ContextCompat.getColor(context, R.color.black)
        strokeWidth = 20f
        style = Paint.Style.FILL
    }
    //endregion :::GLOBALS---->>

    //region :::DRAWING-->>

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(coloLight)
        canvas.drawRect(0f, 0f, dimen.x * prog, dimen.y.toFloat(), paintArc)
    }

    //endregion :::DRAWING-->>

    //region COMMUNICATIONS---->>

    fun updateProg(currentBytes: Float) {
        Log.d("TAG--", "updateProg: $currentBytes")
        this.prog = currentBytes
        postInvalidate()
    }

    //endregion COMMUNICATIONS---->>
}