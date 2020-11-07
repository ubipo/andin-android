package net.pfiers.andin.view

import android.graphics.*
import android.graphics.drawable.Drawable


class LevelTextDrawable(private val text: String) : Drawable() {
    private val paint: Paint = Paint()

    override fun draw(canvas: Canvas) {
        initPaint()
        canvas.drawText(text, (bounds.width().toFloat() * (1.0 / 5.0)).toFloat(),
            (bounds.height().toFloat() * (4.0 / 5.0)).toFloat(), paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(cf: ColorFilter?) {
        paint.colorFilter = cf
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    private fun initPaint() {
        paint.color = Color.WHITE
        paint.textSize = bounds.width().toFloat()
        paint.isAntiAlias = true
        paint.isFakeBoldText = true
    }
}
