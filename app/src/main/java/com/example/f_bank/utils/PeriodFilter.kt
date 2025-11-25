package com.example.f_bank.utils

import com.example.f_bank.data.Transaction
import java.util.Calendar

enum class PeriodType {
    DAY, WEEK, MONTH, YEAR
}

object PeriodFilter {
    fun filterTransactions(transactions: List<Transaction>, period: PeriodType): List<Transaction> {
        val now = Calendar.getInstance()
        val startTime = when (period) {
            PeriodType.DAY -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            PeriodType.WEEK -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            PeriodType.MONTH -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            PeriodType.YEAR -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
        }
        
        return transactions.filter { transaction ->
            transaction.timestamp >= startTime && transaction.timestamp <= now.timeInMillis
        }
    }
    
    fun getPeriodDisplayName(period: PeriodType): String {
        return when (period) {
            PeriodType.DAY -> "День"
            PeriodType.WEEK -> "Неделя"
            PeriodType.MONTH -> "Месяц"
            PeriodType.YEAR -> "Год"
        }
    }
}

