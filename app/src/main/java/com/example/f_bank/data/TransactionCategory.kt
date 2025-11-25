package com.example.f_bank.data

enum class TransactionCategory(val displayName: String, val parentCategory: String) {
    // РАСХОДЫ - Продукты и бакалея
    GROCERIES_SUPERMARKET("Супермаркеты", "Продукты и бакалея"),
    GROCERIES_MARKET("Рынки", "Продукты и бакалея"),
    GROCERIES_DELIVERY("Доставка еды (продукты)", "Продукты и бакалея"),
    
    // РАСХОДЫ - Кафе и рестораны
    RESTAURANT("Рестораны / Бары / Кофейни", "Кафе и рестораны"),
    FOOD_DELIVERY("Доставка готовой еды", "Кафе и рестораны"),
    FASTFOOD("Фастфуд", "Кафе и рестораны"),
    
    // РАСХОДЫ - Транспорт
    TRANSPORT_PUBLIC("Общественный транспорт", "Транспорт"),
    TRANSPORT_TAXI("Такси", "Транспорт"),
    TRANSPORT_CARSHARING("Каршеринг / Аренда авто", "Транспорт"),
    TRANSPORT_FUEL("Топливо", "Транспорт"),
    TRANSPORT_REPAIR("Ремонт и обслуживание авто", "Транспорт"),
    TRANSPORT_FINES("Штрафы", "Транспорт"),
    
    // РАСХОДЫ - Жилье и коммуналка
    HOUSING_RENT("Аренда жилья", "Жилье и коммуналка"),
    HOUSING_UTILITIES("Коммунальные услуги", "Жилье и коммуналка"),
    HOUSING_INTERNET("Интернет, ТВ, домофон", "Жилье и коммуналка"),
    HOUSING_REPAIR("Ремонт и мебель", "Жилье и коммуналка"),
    HOUSING_SUPPLIES("Бытовая химия и хозтовары", "Жилье и коммуналка"),
    
    // РАСХОДЫ - Одежда и обувь
    CLOTHING("Одежда", "Одежда и обувь"),
    SHOES("Обувь", "Одежда и обувь"),
    ACCESSORIES("Аксессуары", "Одежда и обувь"),
    
    // РАСХОДЫ - Здоровье и красота
    HEALTH_PHARMACY("Аптеки", "Здоровье и красота"),
    HEALTH_MEDICAL("Поликлиники / Стоматология", "Здоровье и красота"),
    HEALTH_FITNESS("Фитнес / Спортзал", "Здоровье и красота"),
    HEALTH_COSMETICS("Косметика / Парфюмерия", "Здоровье и красота"),
    
    // РАСХОДЫ - Развлечения и хобби
    ENTERTAINMENT_CINEMA("Кино / Театры / Концерты", "Развлечения и хобби"),
    ENTERTAINMENT_BOOKS("Книги / Журналы", "Развлечения и хобби"),
    ENTERTAINMENT_HOBBIES("Хобби", "Развлечения и хобби"),
    ENTERTAINMENT_GAMES("Игры / Подписки", "Развлечения и хобби"),
    ENTERTAINMENT_TRAVEL("Отдых / Путешествия", "Развлечения и хобби"),
    
    // РАСХОДЫ - Связь
    COMMUNICATION_MOBILE("Мобильная связь", "Связь"),
    COMMUNICATION_LANDLINE("Стационарный телефон", "Связь"),
    
    // РАСХОДЫ - Образование
    EDUCATION_COURSES("Курсы / Вебинары", "Образование"),
    EDUCATION_BOOKS("Книги (нехудожественные)", "Образование"),
    EDUCATION_TUTOR("Репетиторы", "Образование"),
    
    // РАСХОДЫ - Подарки и благотворительность
    GIFTS("Подарки", "Подарки и благотворительность"),
    CHARITY("Благотворительность", "Подарки и благотворительность"),
    
    // РАСХОДЫ - Дети
    CHILDREN_GOODS("Детские товары", "Дети"),
    CHILDREN_ACTIVITIES("Кружки / Секции", "Дети"),
    CHILDREN_TOYS("Игрушки", "Дети"),
    
    // РАСХОДЫ - Финансовые операции
    FINANCE_TRANSFER("Переводы", "Финансовые операции"),
    FINANCE_LOAN("Погашение кредитов", "Финансовые операции"),
    FINANCE_INSURANCE("Страховки", "Финансовые операции"),
    FINANCE_TAXES("Налоги", "Финансовые операции"),
    FINANCE_FEES("Комиссии банка", "Финансовые операции"),
    
    // РАСХОДЫ - Прочие
    OTHER_EXPENSE("Прочие расходы", "Прочие"),
    
    // ДОХОДЫ
    INCOME_SALARY("Зарплата", "Доходы"),
    INCOME_FREELANCE("Подработка / Фриланс", "Доходы"),
    INCOME_INTEREST("Проценты по вкладам", "Доходы"),
    INCOME_INVESTMENTS("Инвестиции", "Доходы"),
    INCOME_GIFTS("Денежные подарки", "Доходы"),
    INCOME_RETURNS("Возврат товаров", "Доходы"),
    INCOME_TRANSFER("Переводы от других", "Доходы"),
    INCOME_SOCIAL("Социальные выплаты", "Доходы"),
    INCOME_DEPOSIT("Пополнение счета", "Доходы");
    
    fun isExpense(): Boolean {
        return !isIncome()
    }
    
    fun isIncome(): Boolean = !isExpense()
    
    companion object {
        fun fromString(name: String?): TransactionCategory? {
            return try {
                if (name == null) null else valueOf(name)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        
        fun getExpenseCategories(): List<TransactionCategory> {
            return values().filter { it.isExpense() }
        }
        
        fun getIncomeCategories(): List<TransactionCategory> {
            return values().filter { it.isIncome() }
        }
        
        fun getCategoriesByParent(parent: String): List<TransactionCategory> {
            return values().filter { it.parentCategory == parent }
        }
    }
}

