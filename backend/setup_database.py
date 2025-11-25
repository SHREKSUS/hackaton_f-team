"""
Скрипт для настройки базы данных PostgreSQL
Запустите этот скрипт перед первым запуском сервера
"""
import psycopg2
import os
import sys
from dotenv import load_dotenv

# Загружаем переменные окружения
backend_dir = os.path.dirname(os.path.abspath(__file__))
env_path = os.path.join(backend_dir, '.env')
if os.path.exists(env_path):
    load_dotenv(env_path)

def setup_database():
    """Создает базу данных если её нет"""
    # Подключаемся к базе postgres (она всегда существует)
    db_config = {
        'host': os.getenv('DB_HOST', 'localhost'),
        'port': os.getenv('DB_PORT', '5432'),
        'database': 'postgres',  # Подключаемся к стандартной БД
        'user': os.getenv('DB_USER', 'postgres'),
        'password': os.getenv('DB_PASSWORD', 'postgres')
    }
    
    target_db = os.getenv('DB_NAME', 'fbank')
    
    print("=" * 60)
    print("Настройка базы данных PostgreSQL")
    print("=" * 60)
    print(f"Host: {db_config['host']}")
    print(f"Port: {db_config['port']}")
    print(f"User: {db_config['user']}")
    print(f"Target Database: {target_db}")
    print()
    
    try:
        # Подключаемся к postgres
        conn = psycopg2.connect(
            host=db_config['host'],
            port=int(db_config['port']),
            database=db_config['database'],
            user=db_config['user'],
            password=db_config['password'],
            client_encoding='UTF8'
        )
        conn.autocommit = True
        cursor = conn.cursor()
        
        # Проверяем, существует ли база данных
        cursor.execute("SELECT 1 FROM pg_database WHERE datname = %s", (target_db,))
        if cursor.fetchone():
            print(f"[OK] База данных '{target_db}' уже существует")
        else:
            print(f"[INFO] Создание базы данных '{target_db}'...")
            cursor.execute(f'CREATE DATABASE "{target_db}"')
            print(f"[OK] База данных '{target_db}' успешно создана!")
        
        cursor.close()
        conn.close()
        
        # Теперь подключаемся к целевой БД и создаем таблицы
        print(f"\n[INFO] Инициализация таблиц в базе данных '{target_db}'...")
        from bank_server.database import db
        db.init_database()
        print("[OK] Таблицы успешно созданы!")
        
        print("\n" + "=" * 60)
        print("[SUCCESS] База данных настроена и готова к использованию!")
        print("=" * 60)
        return True
        
    except psycopg2.OperationalError as e:
        print(f"\n[ERROR] Ошибка подключения к PostgreSQL: {str(e)}")
        print("\nУбедитесь, что:")
        print("   1. PostgreSQL установлен и запущен")
        print("   2. Параметры подключения в .env файле правильные")
        print("   3. Пользователь имеет права на создание баз данных")
        return False
    except Exception as e:
        print(f"\n[ERROR] Неожиданная ошибка: {str(e)}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == '__main__':
    # Добавляем путь для импорта
    sys.path.insert(0, backend_dir)
    
    if setup_database():
        sys.exit(0)
    else:
        print("\n[ERROR] Настройка базы данных не удалась.")
        print("Исправьте ошибки и попробуйте снова.")
        sys.exit(1)

