package com.example.f_bank.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import com.example.f_bank.data.TransactionCategory
import kotlin.math.*
import java.text.NumberFormat
import java.util.Locale

class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private data class DonutSlice(
        val category: TransactionCategory,
        val value: Double,
        val startAngle: Float,
        val sweepAngle: Float,
        val color: Int
    )
    
    private var slices: List<DonutSlice> = emptyList()
    private var totalAmount: Double = 0.0
    private var titleText: String = ""
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0f
    }
    
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFF111111.toInt()
    }
    
    private val titleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFF555555.toInt()
    }
    
    private val colors = listOf(
        0xFF000000.toInt(), // Черный
        0xFF333333.toInt(), // Темно-серый
        0xFF666666.toInt(), // Серый
        0xFF999999.toInt(), // Светло-серый
        0xFFCCCCCC.toInt(), // Очень светло-серый
    )
    
    fun setData(
        data: List<Pair<TransactionCategory, Double>>,
        total: Double,
        title: String,
        isExpenses: Boolean = false
    ) {
        this.totalAmount = total
        this.titleText = title
        
        if (total == 0.0) {
            slices = emptyList()
            invalidate()
            return
        }
        
        var startAngle = -90f // Начинаем сверху
        val newSlices = mutableListOf<DonutSlice>()
        
        data.forEachIndexed { index, (category, amount) ->
            val percentage = amount / total
            val sweepAngle = (percentage * 360).toFloat()
            
            if (sweepAngle > 0.5f) { // Показываем только сектора больше 0.5 градуса
                newSlices.add(
                    DonutSlice(
                        category = category,
                        value = amount,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        color = colors[index % colors.size]
                    )
                )
                startAngle += sweepAngle
            }
        }
        
        slices = newSlices
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (slices.isEmpty()) return
        
        val centerX = width / 2f
        val centerY = height / 2f
        val outerRadius = minOf(width, height) / 2f - 40f
        val innerRadius = outerRadius * 0.6f // Дырка в центре
        
        val outerRect = RectF(
            centerX - outerRadius,
            centerY - outerRadius,
            centerX + outerRadius,
            centerY + outerRadius
        )
        
        // Рисуем сектора как полные круги
        slices.forEach { slice ->
            paint.color = slice.color
            canvas.drawArc(outerRect, slice.startAngle, slice.sweepAngle, true, paint)
        }
        
        // Вырезаем центр (рисуем белый круг поверх)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(centerX, centerY, innerRadius, paint)
        
        // Рисуем текст в центре
        val numberFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"))
        numberFormat.maximumFractionDigits = 0
        
        val amountText = "${numberFormat.format(totalAmount)} Р"
        val titleY = centerY - 20f
        val amountY = centerY + 20f
        
        titleTextPaint.textSize = 32f
        textPaint.textSize = 48f
        
        canvas.drawText(titleText, centerX, titleY, titleTextPaint)
        canvas.drawText(amountText, centerX, amountY, textPaint)
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = minOf(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
        setMeasuredDimension(size, size)
    }
}

