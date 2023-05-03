package com.tomer.tomershare.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.tomer.tomershare.R

class EndPosterProvider {
    companion object {
        fun Context.getEndPoster(speed: String, volume: String): Bitmap {
            val bmp = Bitmap.createBitmap(1000, 660, Bitmap.Config.ARGB_8888)

            val canvas = Canvas(bmp)
            val ins = this.resources.openRawResource(R.raw.poster)
            val pos = BitmapFactory.decodeStream(ins)
            canvas.drawBitmap(pos, 0f, 0f, null)

            val textPaint = TextPaint().apply {
                isAntiAlias = true
                typeface = ResourcesCompat.getFont(this@getEndPoster, R.font.quantico_bold_italic)
                textSize = 90f
                color = Color.parseColor("#ff0d3f7b")
                setShadowLayer(8f, 4f, 4f, Color.DKGRAY)
            }

            val sts = volume.split(" ")
            val endLim = 380f

            canvas.save()
            canvas.rotate(24f, 100f, 350f)
            canvas.drawText(sts[0], endLim - textPaint.measureText(sts[0]), 300f, textPaint)
            canvas.drawText(sts[1], endLim - textPaint.measureText(sts[1]), 390f, textPaint)
            canvas.restore()

            textPaint.apply {
                textSize = 56f
            }

            canvas.save()
            canvas.rotate(-38f,920f,240f)
            canvas.drawText(speed, 860f - textPaint.measureText(speed), 200f, textPaint)
            canvas.drawText("MB/s", 900f - textPaint.measureText("MB/s"), 260f, textPaint)
            canvas.restore()

            return bmp
        }
    }
}