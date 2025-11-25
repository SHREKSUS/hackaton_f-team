import psycopg2
import psycopg2.extras
import psycopg2.errors
import os
import sys
from contextlib import contextmanager
from dotenv import load_dotenv

# Загружаем переменные окружения из .env файла
backend_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
env_path = os.path.join(backend_dir, '.env')
if os.path.exists(env_path):
    load_dotenv(env_path)


class Database:
    def __init__(self):
        # Получаем параметры подключения из переменных окружения
        # Используем безопасное получение значений с обработкой кодировки
        try:
            self.db_config = {
                'host': str(os.getenv('DB_HOST', 'localhost')),
                'port': str(os.getenv('DB_PORT', '5432')),
                'database': str(os.getenv('DB_NAME', 'fbank')),
                'user': str(os.getenv('DB_USER', 'postgres')),
                'password': str(os.getenv('DB_PASSWORD', 'postgres'))
            }
        except Exception as e:
            # Если есть проблема с кодировкой переменных окружения, используем значения по умолчанию
            self.db_config = {
                'host': 'localhost',
                'port': '5432',
                'database': 'fbank',
                'user': 'postgres',
                'password': 'postgres'
            }
        
        # Отложенная инициализация - не вызываем init_database() при создании
        # БД будет инициализирована при первом использовании get_connection()
        self._initialized = False
        
        # Пробуем инициализировать БД, но не останавливаем выполнение при ошибке
        # Сервер должен запуститься даже если БД недоступна
        try:
            self.init_database()
            self._initialized = True
        except Exception as e:
            # Не останавливаем выполнение - сервер может запуститься без БД
            # БД будет инициализирована при первом запросе
            error_msg = str(e).encode('utf-8', errors='replace').decode('utf-8')
            error_type = type(e).__name__
            print(f"\n[WARNING] Database initialization failed: {error_msg}")
            print(f"   Error type: {error_type}")
            print("   Server will start, but database operations may fail.")
            print("   Make sure PostgreSQL is running and database 'fbank' exists.")
            self._initialized = False

    @contextmanager
    def get_connection(self):
        """Контекстный менеджер для подключения к БД"""
        conn = None
        try:
            # Используем отдельные параметры вместо DSN строки для лучшей обработки кодировки
            # Убеждаемся, что все строки правильно закодированы
            host = str(self.db_config['host']).encode('utf-8', errors='replace').decode('utf-8')
            port = str(self.db_config['port'])
            database = str(self.db_config['database']).encode('utf-8', errors='replace').decode('utf-8')
            user = str(self.db_config['user']).encode('utf-8', errors='replace').decode('utf-8')
            password = str(self.db_config['password']).encode('utf-8', errors='replace').decode('utf-8')
            
            conn = psycopg2.connect(
                host=host,
                port=int(port),
                database=database,
                user=user,
                password=password,
                client_encoding='UTF8'
            )
        except psycopg2.OperationalError as e:
            error_str = str(e).encode('utf-8', errors='replace').decode('utf-8')
            # Выводим информацию о подключении для диагностики
            print(f"\n[ERROR] Ошибка подключения к PostgreSQL:")
            print(f"   Host: {host}")
            print(f"   Port: {port}")
            print(f"   Database: {database}")
            print(f"   User: {user}")
            print(f"   Ошибка: {error_str}")
            print(f"\nПроверьте:")
            print(f"   1. PostgreSQL запущен?")
            print(f"   2. База данных '{database}' существует?")
            print(f"   3. Пользователь '{user}' имеет права доступа?")
            print(f"   4. Пароль правильный?")
            raise psycopg2.OperationalError(f"Failed to connect to PostgreSQL: {error_str}")
        except (UnicodeDecodeError, UnicodeError, UnicodeEncodeError) as e:
            # Преобразуем UnicodeDecodeError в более понятное сообщение
            # Не передаем технические детали пользователю
            raise Exception("Database encoding error") from e
        except Exception as e:
            # Ловим все остальные ошибки для лучшей диагностики
            error_str = str(e).encode('utf-8', errors='replace').decode('utf-8')
            if 'codec' in error_str.lower() or 'utf' in error_str.lower() or 'encoding' in error_str.lower():
                # Не передаем технические детали пользователю
                raise Exception("Database encoding error") from e
            raise
        
        try:
            yield conn
            # Коммитим только если autocommit выключен
            if not conn.closed and not conn.autocommit:
                conn.commit()
        except Exception as e:
            # Делаем rollback только если соединение открыто и autocommit выключен
            if not conn.closed and not conn.autocommit:
                try:
                    conn.rollback()
                except psycopg2.InterfaceError:
                    # Соединение уже закрыто, игнорируем
                    pass
            raise e
        finally:
            if conn and not conn.closed:
                conn.close()

    def init_database(self):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            
            # Используем autocommit для DDL операций, чтобы избежать проблем с транзакциями
            conn.autocommit = True

            try:
                # Таблица пользователей
                cursor.execute('''
                    CREATE TABLE IF NOT EXISTS users (
                        id SERIAL PRIMARY KEY,
                        phone VARCHAR(20) UNIQUE NOT NULL,
                        password VARCHAR(255) NOT NULL,
                        name VARCHAR(255) NOT NULL,
                        pin_hash VARCHAR(255),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                ''')
                
                # Добавляем колонку pin_hash если её нет (для существующих БД)
                try:
                    cursor.execute('ALTER TABLE users ADD COLUMN pin_hash VARCHAR(255)')
                except psycopg2.errors.DuplicateColumn:
                    # Колонка уже существует, игнорируем ошибку
                    pass
                except Exception as e:
                    # Другие ошибки игнорируем (например, если таблица не существует)
                    pass

                # Таблица счетов
                cursor.execute('''
                    CREATE TABLE IF NOT EXISTS accounts (
                        id SERIAL PRIMARY KEY,
                        user_id INTEGER NOT NULL,
                        balance NUMERIC(15, 2) DEFAULT 0.0,
                        currency VARCHAR(3) DEFAULT 'KZT',
                        name VARCHAR(255) NOT NULL,
                        number VARCHAR(50) UNIQUE NOT NULL,
                        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
                    )
                ''')

                # Таблица транзакций
                cursor.execute('''
                    CREATE TABLE IF NOT EXISTS transactions (
                        id SERIAL PRIMARY KEY,
                        user_id INTEGER NOT NULL,
                        amount NUMERIC(15, 2) NOT NULL,
                        type VARCHAR(50) NOT NULL,
                        description TEXT,
                        from_account VARCHAR(50),
                        to_account VARCHAR(50),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
                    )
                ''')

                # Таблица карт
                cursor.execute('''
                    CREATE TABLE IF NOT EXISTS cards (
                        id SERIAL PRIMARY KEY,
                        user_id INTEGER NOT NULL,
                        number VARCHAR(255) NOT NULL,
                        type VARCHAR(50) NOT NULL,
                        balance NUMERIC(15, 2) DEFAULT 0.0,
                        currency VARCHAR(3) DEFAULT 'KZT',
                        expiry VARCHAR(10),
                        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
                    )
                ''')
                
                # Увеличиваем размер поля number если оно меньше 255 (для существующих БД)
                # Это нужно для хранения зашифрованных номеров карт (Base64, ~60 символов)
                try:
                    # Проверяем текущий размер поля
                    cursor.execute("""
                        SELECT character_maximum_length 
                        FROM information_schema.columns 
                        WHERE table_name = 'cards' AND column_name = 'number'
                    """)
                    result = cursor.fetchone()
                    if result and result[0] and result[0] < 255:
                        cursor.execute('ALTER TABLE cards ALTER COLUMN number TYPE VARCHAR(255)')
                except Exception as e:
                    # Игнорируем ошибки (колонка может уже иметь нужный размер или таблица не существует)
                    pass

                # Возвращаем autocommit обратно для тестовых данных
                conn.autocommit = False
                
                # Добавляем тестовые данные
                self.add_test_data(cursor)
            except Exception as e:
                # Если произошла ошибка, делаем rollback только если autocommit выключен
                if not conn.autocommit:
                    conn.rollback()
                raise e

    def add_test_data(self, cursor):
        try:
            # Проверяем, есть ли уже тестовый пользователь
            cursor.execute("SELECT id FROM users WHERE phone = %s", ('77071234567',))
            if cursor.fetchone() is None:
                # Добавляем пользователя
                cursor.execute(
                    "INSERT INTO users (phone, password, name) VALUES (%s, %s, %s) RETURNING id",
                    ('77071234567', 'test123', 'Иван Иванов')
                )
                user_id = cursor.fetchone()[0]
     
                # Добавляем счета
                cursor.execute(
                    "INSERT INTO accounts (user_id, balance, currency, name, number) VALUES (%s, %s, %s, %s, %s)",
                    (user_id, 15000.0, 'KZT', 'Основной счет', 'KZ123456789')
                )
                cursor.execute(
                    "INSERT INTO accounts (user_id, balance, currency, name, number) VALUES (%s, %s, %s, %s, %s)",
                    (user_id, 5000.0, 'KZT', 'Накопительный счет', 'KZ987654321')
                )

                # Добавляем карты
                cursor.execute(
                    "INSERT INTO cards (user_id, number, type, balance, currency, expiry) VALUES (%s, %s, %s, %s, %s, %s)",
                    (user_id, '**** 5678', 'Visa', 15000, 'KZT', '12/25')
                )
                cursor.execute(
                    "INSERT INTO cards (user_id, number, type, balance, currency, expiry) VALUES (%s, %s, %s, %s, %s, %s)",
                    (user_id, '**** 1234', 'Mastercard', 5000, 'KZT', '08/24')
                )

                # Добавляем тестовые транзакции
                test_transactions = [
                    (user_id, -2000.0, 'transfer', 'Перевод Карине',
                     'KZ123456789', 'KZ555555555'),
                    (user_id, 50000.0, 'deposit', 'Зарплата', None, 'KZ123456789'),
                    (user_id, -1500.0, 'payment', 'Казахтелеком',
                     'KZ123456789', 'KZ777777777')
                ]

                cursor.executemany(
                    "INSERT INTO transactions (user_id, amount, type, description, from_account, to_account) VALUES (%s, %s, %s, %s, %s, %s)",
                    test_transactions
                )
        except Exception as e:
            # Игнорируем ошибки при добавлении тестовых данных
            # (например, если данные уже существуют или произошла другая ошибка)
            pass


# Глобальный экземпляр базы данных
db = Database()
