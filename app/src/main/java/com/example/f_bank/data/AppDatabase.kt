package com.example.f_bank.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [User::class, Card::class, TransactionEntity::class, FavoriteContactEntity::class], version = 11, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun cardDao(): CardDao
    abstract fun transactionDao(): TransactionDao
    abstract fun favoriteContactDao(): FavoriteContactDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS cards (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        number TEXT NOT NULL,
                        type TEXT NOT NULL,
                        balance REAL NOT NULL,
                        currency TEXT NOT NULL,
                        expiry TEXT,
                        isLinked INTEGER NOT NULL
                    )
                """)
            }
        }
        
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Миграция: замена email на phone
                // Создаем новую таблицу с phone
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS users_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        phone TEXT NOT NULL,
                        password TEXT NOT NULL
                    )
                """)
                
                // Копируем данные из старой таблицы (email -> phone)
                database.execSQL("""
                    INSERT INTO users_new (id, name, phone, password)
                    SELECT id, name, email, password FROM users
                """)
                
                // Удаляем старую таблицу
                database.execSQL("DROP TABLE users")
                
                // Переименовываем новую таблицу
                database.execSQL("ALTER TABLE users_new RENAME TO users")
                
                // Создаем индекс на phone
                database.execSQL("CREATE INDEX IF NOT EXISTS index_users_phone ON users(phone)")
            }
        }
        
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Добавляем поля displayOrder и isHidden в таблицу cards
                database.execSQL("ALTER TABLE cards ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE cards ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Добавляем поле isDeleted для soft delete
                database.execSQL("ALTER TABLE cards ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Добавляем поля для блокировки и лимитов
                database.execSQL("ALTER TABLE cards ADD COLUMN isBlocked INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE cards ADD COLUMN dailyLimit REAL")
                database.execSQL("ALTER TABLE cards ADD COLUMN monthlyLimit REAL")
            }
        }
        
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Создаем таблицу транзакций
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        cardId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        amount REAL NOT NULL,
                        type TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        iconRes INTEGER NOT NULL DEFAULT 0,
                        toCardId INTEGER,
                        fromCardId INTEGER
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_userId ON transactions(userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_cardId ON transactions(cardId)")
            }
        }
        
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Добавляем поле linkedPhone в таблицу cards
                database.execSQL("ALTER TABLE cards ADD COLUMN linkedPhone TEXT")
            }
        }
        
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Создаем таблицу избранных контактов
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS favorite_contacts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        phone TEXT,
                        cardNumber TEXT,
                        createdAt INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_contacts_userId ON favorite_contacts(userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_contacts_phone ON favorite_contacts(phone)")
            }
        }
        
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Исправляем структуру таблицы favorite_contacts, если она была создана неправильно
                // Удаляем старую таблицу и создаем заново с правильной структурой
                database.execSQL("DROP TABLE IF EXISTS favorite_contacts")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS favorite_contacts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        phone TEXT,
                        cardNumber TEXT,
                        createdAt INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_contacts_userId ON favorite_contacts(userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_contacts_phone ON favorite_contacts(phone)")
            }
        }
        
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Добавляем поле category в таблицу transactions
                database.execSQL("ALTER TABLE transactions ADD COLUMN category TEXT")
            }
        }
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Удаляем старую локальную базу данных только один раз (при первом запуске после обновления)
                val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                   val dbClearedKey = "db_cleared_v11"
                
                if (!prefs.getBoolean(dbClearedKey, false)) {
                    val dbFile = context.applicationContext.getDatabasePath("fbank_database")
                    val dbShmFile = context.applicationContext.getDatabasePath("fbank_database-shm")
                    val dbWalFile = context.applicationContext.getDatabasePath("fbank_database-wal")
                    
                    try {
                        if (dbFile.exists()) dbFile.delete()
                        if (dbShmFile.exists()) dbShmFile.delete()
                        if (dbWalFile.exists()) dbWalFile.delete()
                        
                        // Помечаем, что база данных была очищена
                        prefs.edit().putBoolean(dbClearedKey, true).apply()
                    } catch (e: Exception) {
                        // Игнорируем ошибки удаления
                    }
                }
                
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fbank_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .fallbackToDestructiveMigration() // Для разработки - удаляет БД при ошибке миграции
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

