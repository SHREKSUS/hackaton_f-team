"""
Скрипт для тестирования регистрации через API
"""
import requests
import json
import sys
import os
from dotenv import load_dotenv

# Загружаем переменные окружения
backend_dir = os.path.dirname(os.path.abspath(__file__))
env_path = os.path.join(backend_dir, '.env')
if os.path.exists(env_path):
    load_dotenv(env_path)

BASE_URL = "http://localhost:5000"

def test_registration():
    """Тестирует регистрацию пользователя"""
    print("=" * 60)
    print("Тест регистрации через API")
    print("=" * 60)
    
    # Тестовые данные
    test_data = {
        "name": "Тестовый Пользователь",
        "phone": "77771234567",
        "password": "test123456"
    }
    
    print(f"\nОтправка запроса на {BASE_URL}/api/auth/register")
    print(f"Данные: {json.dumps(test_data, ensure_ascii=False, indent=2)}")
    print()
    
    try:
        response = requests.post(
            f"{BASE_URL}/api/auth/register",
            json=test_data,
            headers={"Content-Type": "application/json"},
            timeout=10
        )
        
        print(f"Статус код: {response.status_code}")
        print(f"Ответ сервера:")
        
        try:
            response_json = response.json()
            print(json.dumps(response_json, ensure_ascii=False, indent=2))
            
            if response_json.get("success"):
                print("\n[OK] Регистрация успешна!")
                return True
            else:
                print(f"\n[ERROR] Регистрация не удалась: {response_json.get('message', 'Неизвестная ошибка')}")
                return False
        except:
            print(response.text)
            print("\n[ERROR] Не удалось распарсить JSON ответ")
            return False
            
    except requests.exceptions.ConnectionError:
        print(f"\n[ERROR] Не удалось подключиться к серверу {BASE_URL}")
        print("Убедитесь, что сервер запущен: python bank_server/server.py")
        return False
    except requests.exceptions.Timeout:
        print("\n[ERROR] Превышено время ожидания ответа сервера")
        return False
    except Exception as e:
        print(f"\n[ERROR] Неожиданная ошибка: {str(e)}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == '__main__':
    if test_registration():
        print("\n" + "=" * 60)
        print("[SUCCESS] Тест пройден успешно!")
        print("=" * 60)
        sys.exit(0)
    else:
        print("\n" + "=" * 60)
        print("[ERROR] Тест не пройден")
        print("=" * 60)
        sys.exit(1)

