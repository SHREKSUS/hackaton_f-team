package com.example.f_bank.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.f_bank.data.TransactionCategory
import kotlin.math.*

class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private data class PieSlice(
        val category: TransactionCategory,
        val value: Double,
        val startAngle: Float,
        val sweepAngle: Float,
        val color: Int
    )
    
    private var slices: List<PieSlice> = emptyList()
    private var onSliceClick: ((TransactionCategory) -> Unit)? = null
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val colors = listOf(
        0xFF4CAF50.toInt(), // Зеленый
        0xFF2196F3.toInt(), // Синий
        0xFFFF9800.toInt(), // Оранжевый
        0xFFE91E63.toInt(), // Розовый
        0xFF9C27B0.toInt(), // Фиолетовый
        0xFF00BCD4.toInt(), // Голубой
        0xFFFFC107.toInt(), // Желтый
        0xFF795548.toInt(), // Коричневый
        0xFF607D8B.toInt(), // Серый
        0xFFF44336.toInt(), // Красный
        0xFF3F51B5.toInt(), // Индиго
        0xFF009688.toInt(), // Бирюзовый
    )
    
    fun setData(
        expenses: List<Pair<TransactionCategory, Double>>,
        incomes: List<Pair<TransactionCategory, Double>>,
        totalExpenses: Double,
        totalIncomes: Double,
        onSliceClick: (TransactionCategory) -> Unit
    ) {
        this.onSliceClick = onSliceClick
        
        val allData = mutableListOf<Pair<TransactionCategory, Double>>()
        
        // Добавляем расходы (отрицательные значения)
        expenses.forEach { (category, amount) ->
            allData.add(category to -amount)
        }
        
        // Добавляем доходы (положительные значения)
        incomes.forEach { (category, amount) ->
            allData.add(category to amount)
        }
        
        val total = abs(totalExpenses) + totalIncomes
        if (total == 0.0) {
            slices = emptyList()
            invalidate()
            return
        }
        
        var startAngle = -90f // Начинаем сверху
        val newSlices = mutableListOf<PieSlice>()
        
        allData.forEachIndexed { index, (category, value) ->
            val percentage = abs(value) / total
            val sweepAngle = (percentage * 360).toFloat()
            
            if (sweepAngle > 0.5f) { // Показываем только сектора больше 0.5 градуса
                newSlices.add(
                    PieSlice(
                        category = category,
                        value = value,
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
    
    fun setExpensesData(
        expenses: List<Pair<TransactionCategory, Double>>,
        totalExpenses: Double,
        onSliceClick: (TransactionCategory) -> Unit
    ) {
        this.onSliceClick = onSliceClick
        
        if (totalExpenses == 0.0) {
            slices = emptyList()
            invalidate()
            return
        }
        
        var startAngle = -90f // Начинаем сверху
        val newSlices = mutableListOf<PieSlice>()
        
        expenses.forEachIndexed { index, (category, amount) ->
            val percentage = amount / totalExpenses
            val sweepAngle = (percentage * 360).toFloat()
            
            if (sweepAngle > 0.5f) { // Показываем только сектора больше 0.5 градуса
                newSlices.add(
                    PieSlice(
                        category = category,
                        value = -amount, // Отрицательное значение для расходов
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
    
    fun setIncomesData(
        incomes: List<Pair<TransactionCategory, Double>>,
        totalIncomes: Double,
        onSliceClick: (TransactionCategory) -> Unit
    ) {
        this.onSliceClick = onSliceClick
        
        if (totalIncomes == 0.0) {
            slices = emptyList()
            invalidate()
            return
        }
        
        var startAngle = -90f // Начинаем сверху
        val newSlices = mutableListOf<PieSlice>()
        
        incomes.forEachIndexed { index, (category, amount) ->
            val percentage = amount / totalIncomes
            val sweepAngle = (percentage * 360).toFloat()
            
            if (sweepAngle > 0.5f) { // Показываем только сектора больше 0.5 градуса
                newSlices.add(
                    PieSlice(
                        category = category,
                        value = amount, // Положительное значение для доходов
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
        val radius = minOf(width, height) / 2f - 40f
        val rect = RectF(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
        
        slices.forEach { slice ->
            paint.color = slice.color
            canvas.drawArc(rect, slice.startAngle, slice.sweepAngle, true, paint)
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val clickedSlice = getSliceAt(event.x, event.y)
            clickedSlice?.let { slice ->
                onSliceClick?.invoke(slice.category)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    
    private fun getSliceAt(x: Float, y: Float): PieSlice? {
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(width, height) / 2f - 40f
        
        val dx = x - centerX
        val dy = y - centerY
        val distance = sqrt(dx * dx + dy * dy)
        
        if (distance > radius) return null
        
        // Вычисляем угол от центра
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        angle += 90f // Смещаем, чтобы 0 был сверху
        if (angle < 0) angle += 360f
        
        // Находим сектор, в который попадает клик
        return slices.find { slice ->
            val endAngle = slice.startAngle + slice.sweepAngle
            when {
                slice.startAngle <= endAngle -> angle >= slice.startAngle && angle <= endAngle
                else -> angle >= slice.startAngle || angle <= endAngle
            }
        }
    }
}

