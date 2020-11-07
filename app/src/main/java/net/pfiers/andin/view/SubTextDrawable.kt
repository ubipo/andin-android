package net.pfiers.andin.view

import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.core.graphics.plus


class SubTextDrawable(private val drawable: Drawable, private val subText: String) : Drawable() {
    private val paint: Paint = Paint()

    override fun draw(canvas: Canvas) {
        initPaint()
        drawable.bounds = bounds.plus(Point(-(bounds.width() * 0.20).toInt(), -(bounds.height() * 0.20).toInt()))
        drawable.draw(canvas)
        canvas.drawText(
            subText,
            (bounds.width().toFloat() * (2.5 / 5.0)).toFloat(),
            (bounds.height().toFloat() * (4.5 / 5.0)).toFloat(),
            paint
        )
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
        paint.setShadowLayer(10F, 0F, 0F, Color.BLACK)
        paint.textSize = bounds.width().toFloat()
        paint.isAntiAlias = true
        paint.isFakeBoldText = true
    }
}
