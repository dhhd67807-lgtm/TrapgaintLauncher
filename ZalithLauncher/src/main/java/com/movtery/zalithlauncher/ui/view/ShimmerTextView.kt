package com.movtery.zalithlauncher.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class ShimmerTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var shimmerPaint: Paint? = null
    private var shimmerAnimator: ValueAnimator? = null
    private val matrix = Matrix()
    private var translateX = 0f

    init {
        setupShimmer()
    }

    private fun setupShimmer() {
        shimmerAnimator = ValueAnimator.ofFloat(-1f, 2f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { animation ->
                translateX = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0) {
            val shader = LinearGradient(
                0f, 0f, w.toFloat(), 0f,
                intArrayOf(
                    currentTextColor,
                    adjustAlpha(currentTextColor, 0.3f),
                    currentTextColor
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            shimmerPaint = Paint(paint).apply {
                this.shader = shader
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        shimmerPaint?.let { paint ->
            val width = width.toFloat()
            matrix.setTranslate(translateX * width, 0f)
            paint.shader.setLocalMatrix(matrix)
            canvas.drawText(
                text.toString(),
                (width - paint.measureText(text.toString())) / 2,
                baseline.toFloat(),
                paint
            )
        } ?: super.onDraw(canvas)
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = Math.round(android.graphics.Color.alpha(color) * factor)
        val red = android.graphics.Color.red(color)
        val green = android.graphics.Color.green(color)
        val blue = android.graphics.Color.blue(color)
        return android.graphics.Color.argb(alpha, red, green, blue)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        shimmerAnimator?.cancel()
    }
}
