"""
Скрипт для проверки подключения к PostgreSQL
"""
import psycopg2
import os
import sys
from dotenv import load_dotenv

# Загружаем переменные окружения из .env файла
backend_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
env_path = os.path.join(backend_dir, '.env')
if os.path.exists(env_path):
    load_dotenv(env_path)

def check_connection():
    """Проверяет подключение к PostgreSQL"""
    db_config = {
        'host': os.getenv('DB_HOST', 'localhost'),
        'port': os.getenv('DB_PORT', '5432'),
        'database': os.getenv('DB_NAME', 'fbank'),
        'user': os.getenv('DB_USER', 'postgres'),
        'password': os.getenv('DB_PASSWORD', 'postgres')
    }
    
    print("Проверка подключения к PostgreSQL...")
    print(f"   Host: {db_config['host']}")
    print(f"   Port: {db_config['port']}")
    print(f"   Database: {db_config['database']}")
    print(f"   User: {db_config['user']}")
    print(f"   Password: {'*' * len(db_config['password'])}")
    print()
    
    try:
        # Сначала пробуем подключиться к базе postgres (она всегда существует)
        # Используем отдельные параметры для лучшей обработки кодировки
        host = str(db_config['host']).encode('utf-8', errors='replace').decode('utf-8')
        port = str(db_config['port'])
        user = str(db_config['user']).encode('utf-8', errors='replace').decode('utf-8')
        password = str(db_config['password']).encode('utf-8', errors='replace').decode('utf-8')
        
        conn = psycopg2.connect(
            host=host,
            port=int(port),
            database='postgres',
            user=user,
            password=password,
            client_encoding='UTF8'
        )
        print("[OK] Подключение к PostgreSQL успешно!")
        
        # Проверяем, существует ли база данных fbank
        cursor = conn.cursor()
        cursor.execute("SELECT 1 FROM pg_database WHERE datname = %s", (db_config['database'],))
        if cursor.fetchone():
            print(f"[OK] База данных '{db_config['database']}' существует")
        else:
            print(f"[ERROR] База данных '{db_config['database']}' не найдена")
            print(f"\nСоздайте базу данных:")
            print(f"   psql -U {db_config['user']} -d postgres")
            print(f"   CREATE DATABASE {db_config['database']};")
            conn.close()
            return False
        
        conn.close()
        return True
        
    except psycopg2.OperationalError as e:
        print(f"[ERROR] Ошибка подключения: {str(e)}")
        print("\nУбедитесь, что:")
        print("   1. PostgreSQL установлен и запущен")
        print("   2. Параметры подключения правильные")
        print("   3. Пользователь имеет права доступа")
        return False
    except (UnicodeDecodeError, UnicodeError, UnicodeEncodeError) as e:
        print(f"[ERROR] Ошибка кодировки: {str(e)}")
        print("\nПопробуйте:")
        print("   1. Установить кодировку UTF-8 в PowerShell:")
        print("      [Console]::OutputEncoding = [System.Text.Encoding]::UTF8")
        print("      [Console]::InputEncoding = [System.Text.Encoding]::UTF8")
        print("   2. Использовать только ASCII символы в пароле")
        print("   3. Установить переменные окружения заново:")
        print("      $env:DB_PASSWORD='ваш_пароль'")
        return False
    except Exception as e:
        error_str = str(e).lower()
        if 'codec' in error_str or 'utf' in error_str or 'unicode' in error_str:
            print(f"[ERROR] Ошибка кодировки: {str(e)}")
            print("\nПопробуйте:")
            print("   1. Установить кодировку UTF-8 в PowerShell:")
            print("      [Console]::OutputEncoding = [System.Text.Encoding]::UTF8")
            print("   2. Использовать только ASCII символы в пароле")
            return False
        print(f"[ERROR] Неожиданная ошибка: {str(e)}")
        print(f"   Тип: {type(e).__name__}")
        return False

if __name__ == '__main__':
    if check_connection():
        print("\n[OK] Все проверки пройдены! Можно запускать server.py")
        sys.exit(0)
    else:
        print("\n[ERROR] Проверка не пройдена. Исправьте ошибки и попробуйте снова.")
        sys.exit(1)

