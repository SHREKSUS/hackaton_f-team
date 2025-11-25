package com.example.f_bank.utils

import java.util.Calendar

enum class QuickPeriodType {
    TODAY,
    YESTERDAY,
    LAST_7_DAYS,
    CURRENT_MONTH,
    LAST_30_DAYS,
    LAST_MONTH,
    CURRENT_QUARTER,
    CURRENT_YEAR,
    YEAR_TO_DATE,
    LAST_YEAR,
    ALL_TIME
}

data class CustomPeriod(
    val startDate: Calendar,
    val endDate: Calendar
)

object PeriodHelper {
    fun getQuickPeriodRange(period: QuickPeriodType): Pair<Long, Long> {
        val now = Calendar.getInstance()
        val start = Calendar.getInstance()
        val end = Calendar.getInstance()
        
        // Устанавливаем конец периода на конец текущего дня
        end.set(Calendar.HOUR_OF_DAY, 23)
        end.set(Calendar.MINUTE, 59)
        end.set(Calendar.SECOND, 59)
        end.set(Calendar.MILLISECOND, 999)
        
        when (period) {
            QuickPeriodType.TODAY -> {
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
            }
            QuickPeriodType.YESTERDAY -> {
                start.add(Calendar.DAY_OF_MONTH, -1)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
                end.add(Calendar.DAY_OF_MONTH, -1)
                end.set(Calendar.HOUR_OF_DAY, 23)
                end.set(Calendar.MINUTE, 59)
                end.set(Calendar.SECOND, 59)
                end.set(Calendar.MILLISECOND, 999)
            }
            QuickPeriodType.LAST_7_DAYS -> {
                start.add(Calendar.DAY_OF_MONTH, -6)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
            }
            QuickPeriodType.CURRENT_MONTH -> {
                start.set(Calendar.DAY_OF_MONTH, 1)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
            }
            QuickPeriodType.LAST_30_DAYS -> {
                start.add(Calendar.DAY_OF_MONTH, -29)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
            }
            QuickPeriodType.LAST_MONTH -> {
                start.add(Calendar.MONTH, -1)
                start.set(Calendar.DAY_OF_MONTH, 1)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
                end.setTimeInMillis(start.timeInMillis)
                end.add(Calendar.MONTH, 1)
                end.add(Calendar.DAY_OF_MONTH, -1)
                end.set(Calendar.HOUR_OF_DAY, 23)
                end.set(Calendar.MINUTE, 59)
                end.set(Calendar.SECOND, 59)
                end.set(Calendar.MILLISECOND, 999)
            }
            QuickPeriodType.CURRENT_QUARTER -> {
                val currentMonth = now.get(Calendar.MONTH)
                val quarterStartMonth = (currentMonth / 3) * 3
                start.set(Calendar.MONTH, quarterStartMonth)
                start.set(Calendar.DAY_OF_MONTH, 1)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
            }
            QuickPeriodType.CURRENT_YEAR -> {
                start.set(Calendar.MONTH, Calendar.JANUARY)
                start.set(Calendar.DAY_OF_MONTH, 1)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
            }
            QuickPeriodType.YEAR_TO_DATE -> {
                start.set(Calendar.MONTH, Calendar.JANUARY)
                start.set(Calendar.DAY_OF_MONTH, 1)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
            }
            QuickPeriodType.LAST_YEAR -> {
                start.add(Calendar.YEAR, -1)
                start.set(Calendar.MONTH, Calendar.JANUARY)
                start.set(Calendar.DAY_OF_MONTH, 1)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
                end.set(Calendar.MONTH, Calendar.DECEMBER)
                end.set(Calendar.DAY_OF_MONTH, 31)
                end.set(Calendar.HOUR_OF_DAY, 23)
                end.set(Calendar.MINUTE, 59)
                end.set(Calendar.SECOND, 59)
                end.set(Calendar.MILLISECOND, 999)
                end.add(Calendar.YEAR, -1)
            }
            QuickPeriodType.ALL_TIME -> {
                start.set(Calendar.YEAR, 2000)
                start.set(Calendar.MONTH, Calendar.JANUARY)
                start.set(Calendar.DAY_OF_MONTH, 1)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
            }
        }
        
        return Pair(start.timeInMillis, end.timeInMillis)
    }
    
    fun formatPeriodDisplay(start: Long, end: Long): String {
        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        val endCal = Calendar.getInstance().apply { timeInMillis = end }
        
        val monthNames = arrayOf("янв", "фев", "мар", "апр", "май", "июн",
            "июл", "авг", "сен", "окт", "ноя", "дек")
        
        val startDay = startCal.get(Calendar.DAY_OF_MONTH)
        val startMonth = monthNames[startCal.get(Calendar.MONTH)]
        val startYear = startCal.get(Calendar.YEAR)
        
        val endDay = endCal.get(Calendar.DAY_OF_MONTH)
        val endMonth = monthNames[endCal.get(Calendar.MONTH)]
        val endYear = endCal.get(Calendar.YEAR)
        
        return if (startYear == endYear) {
            if (startMonth == endMonth) {
                if (startDay == endDay) {
                    "$startDay $startMonth $startYear"
                } else {
                    "$startDay–$endDay $startMonth $startYear"
                }
            } else {
                "$startDay $startMonth – $endDay $endMonth $startYear"
            }
        } else {
            "$startDay $startMonth $startYear – $endDay $endMonth $endYear"
        }
    }
}

