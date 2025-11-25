package com.example.f_bank.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.f_bank.R

class PinIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var pinLength: Int = 0
    private var maxLength: Int = 4
    private var state: PinState = PinState.Normal

    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = ContextCompat.getColor(context, R.color.input_stroke)
    }

    private val filledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val successPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.pin_success)
    }

    private val errorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.pin_error)
    }

    enum class PinState {
        Normal, Success, Error
    }

    fun setPinLength(length: Int) {
        pinLength = length.coerceIn(0, maxLength)
        invalidate()
    }

    fun setState(newState: PinState) {
        state = newState
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val circleRadius = height / 2f - 8f
        val spacing = width / (maxLength + 1f)
        val startX = spacing

        for (i in 0 until maxLength) {
            val centerX = startX + i * spacing
            val centerY = height / 2f

            val paint = when {
                i < pinLength -> {
                    when (state) {
                        PinState.Success -> successPaint
                        PinState.Error -> errorPaint
                        PinState.Normal -> filledPaint.apply {
                            color = ContextCompat.getColor(context, R.color.button_primary_bg)
                        }
                    }
                }
                else -> emptyPaint
            }

            if (i < pinLength) {
                canvas.drawCircle(centerX, centerY, circleRadius, paint)
            } else {
                canvas.drawCircle(centerX, centerY, circleRadius, paint)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = suggestedMinimumWidth + paddingLeft + paddingRight
        val desiredHeight = suggestedMinimumHeight + paddingTop + paddingBottom

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }
}

