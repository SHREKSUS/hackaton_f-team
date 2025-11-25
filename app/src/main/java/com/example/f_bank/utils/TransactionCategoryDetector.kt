package com.example.f_bank.utils

import com.example.f_bank.data.TransactionCategory
import com.example.f_bank.data.TransactionType

object TransactionCategoryDetector {
    /**
     * Автоматически определяет категорию транзакции на основе описания и типа
     */
    fun detectCategory(description: String, type: TransactionType, amount: Double): TransactionCategory? {
        val lowerDescription = description.lowercase()
        
        // Определяем категорию на основе типа транзакции
        when (type) {
            TransactionType.DEPOSIT -> {
                return TransactionCategory.INCOME_DEPOSIT
            }
            TransactionType.TRANSFER_IN -> {
                // Входящий перевод - это доход
                if (lowerDescription.contains("перевод") || lowerDescription.contains("от")) {
                    return TransactionCategory.INCOME_TRANSFER
                }
                return TransactionCategory.INCOME_DEPOSIT
            }
            TransactionType.TRANSFER_OUT -> {
                // Исходящий перевод - это расход
                if (lowerDescription.contains("перевод") || lowerDescription.contains("по номеру")) {
                    return TransactionCategory.FINANCE_TRANSFER
                }
                return TransactionCategory.FINANCE_TRANSFER
            }
            TransactionType.PURCHASE -> {
                // Покупка - определяем по описанию
                return detectCategoryByDescription(lowerDescription)
            }
        }
        
        return null
    }
    
    private fun detectCategoryByDescription(description: String): TransactionCategory {
        // Продукты и бакалея
        if (description.contains("супермаркет") || description.contains("магазин") || 
            description.contains("продукты") || description.contains("еда") ||
            description.contains("ашан") || description.contains("пятерочка") ||
            description.contains("магнит") || description.contains("перекресток")) {
            return TransactionCategory.GROCERIES_SUPERMARKET
        }
        
        if (description.contains("рынок") || description.contains("базар")) {
            return TransactionCategory.GROCERIES_MARKET
        }
        
        // Кафе и рестораны
        if (description.contains("ресторан") || description.contains("кафе") ||
            description.contains("бар") || description.contains("кофейня")) {
            return TransactionCategory.RESTAURANT
        }
        
        if (description.contains("доставка") && (description.contains("еда") || description.contains("еды"))) {
            return TransactionCategory.FOOD_DELIVERY
        }
        
        if (description.contains("фастфуд") || description.contains("макдональдс") ||
            description.contains("kfc") || description.contains("бургер")) {
            return TransactionCategory.FASTFOOD
        }
        
        // Транспорт
        if (description.contains("метро") || description.contains("автобус") ||
            description.contains("троллейбус") || description.contains("трамвай")) {
            return TransactionCategory.TRANSPORT_PUBLIC
        }
        
        if (description.contains("такси") || description.contains("яндекс.го") ||
            description.contains("uber") || description.contains("gett")) {
            return TransactionCategory.TRANSPORT_TAXI
        }
        
        if (description.contains("каршеринг") || description.contains("аренда авто")) {
            return TransactionCategory.TRANSPORT_CARSHARING
        }
        
        if (description.contains("заправка") || description.contains("бензин") ||
            description.contains("топливо") || description.contains("азс")) {
            return TransactionCategory.TRANSPORT_FUEL
        }
        
        if (description.contains("ремонт") && description.contains("авто")) {
            return TransactionCategory.TRANSPORT_REPAIR
        }
        
        if (description.contains("штраф") || description.contains("гибдд") ||
            description.contains("парковка")) {
            return TransactionCategory.TRANSPORT_FINES
        }
        
        // Жилье и коммуналка
        if (description.contains("аренда") || description.contains("квартир")) {
            return TransactionCategory.HOUSING_RENT
        }
        
        if (description.contains("жкх") || description.contains("коммунал") ||
            description.contains("электричество") || description.contains("газ") ||
            description.contains("вода")) {
            return TransactionCategory.HOUSING_UTILITIES
        }
        
        if (description.contains("интернет") || description.contains("тв") ||
            description.contains("домофон")) {
            return TransactionCategory.HOUSING_INTERNET
        }
        
        if (description.contains("ремонт") && !description.contains("авто")) {
            return TransactionCategory.HOUSING_REPAIR
        }
        
        // Здоровье
        if (description.contains("аптека") || description.contains("лекарств")) {
            return TransactionCategory.HEALTH_PHARMACY
        }
        
        if (description.contains("поликлиник") || description.contains("стоматолог") ||
            description.contains("анализ")) {
            return TransactionCategory.HEALTH_MEDICAL
        }
        
        if (description.contains("фитнес") || description.contains("спортзал") ||
            description.contains("бассейн")) {
            return TransactionCategory.HEALTH_FITNESS
        }
        
        // Связь
        if (description.contains("связь") || description.contains("мобильн")) {
            return TransactionCategory.COMMUNICATION_MOBILE
        }
        
        // По умолчанию - прочие расходы
        return TransactionCategory.OTHER_EXPENSE
    }
}

