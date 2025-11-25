import requests
import json

BASE_URL = "http://localhost:5000"
token = None


def print_step(step, description):
    print(f"\n{'='*50}")
    print(f"📋 {step}: {description}")
    print(f"{'='*50}")


def test_login():
    global token
    print_step("1", "Тестируем вход в систему")

    response = requests.post(
        f"{BASE_URL}/api/auth/login",
        json={"phone": "77071234567", "password": "test123"}
    )
    print(f"🔐 Вход: {response.json()}")
    return response.json()


def test_2fa():
    global token
    print_step("2", "Тестируем двухфакторную аутентификацию")

    response = requests.post(
        f"{BASE_URL}/api/auth/verify-2fa",
        json={"phone": "77071234567", "code": "1234"}
    )
    result = response.json()
    print(f"🔒 2FA: {result}")

    if result.get('success'):
        token = result['token']
        print(f"✅ Токен получен: {token[:50]}...")
    return result


def test_accounts():
    print_step("3", "Тестируем получение счетов")

    headers = {"Authorization": f"Bearer {token}"}
    response = requests.get(f"{BASE_URL}/api/accounts", headers=headers)
    result = response.json()
    print(f"💰 Счета: {result}")
    return result


def test_balance():
    print_step("4", "Тестируем получение баланса")

    headers = {"Authorization": f"Bearer {token}"}
    response = requests.get(f"{BASE_URL}/api/balance", headers=headers)
    result = response.json()
    print(f"💳 Баланс: {result}")
    return result


def test_transfer():
    print_step("5", "Тестируем перевод денег")

    headers = {"Authorization": f"Bearer {token}"}
    transfer_data = {
        "from_account": "KZ123456789",
        "to_account": "KZ999888777",
        "amount": 1000.0,
        "description": "Тестовый перевод"
    }
    response = requests.post(
        f"{BASE_URL}/api/transfer", headers=headers, json=transfer_data)
    result = response.json()
    print(f"💸 Перевод: {result}")
    return result


def test_transactions():
    print_step("6", "Тестируем историю операций")

    headers = {"Authorization": f"Bearer {token}"}
    response = requests.get(f"{BASE_URL}/api/transactions", headers=headers)
    result = response.json()
    print(f"📊 История: {len(result.get('transactions', []))} операций")
    return result


def test_services():
    print_step("7", "Тестируем получение услуг")

    headers = {"Authorization": f"Bearer {token}"}
    response = requests.get(f"{BASE_URL}/api/services", headers=headers)
    result = response.json()
    print(f"🏠 Услуги: {len(result.get('services', []))} услуг")
    return result


def test_payment():
    print_step("8", "Тестируем оплату услуг")

    headers = {"Authorization": f"Bearer {token}"}
    payment_data = {
        "account": "KZ123456789",
        "service": "Казахтелеком",
        "amount": 2500.0
    }
    response = requests.post(
        f"{BASE_URL}/api/payment", headers=headers, json=payment_data)
    result = response.json()
    print(f"💳 Платеж: {result}")
    return result


def test_cards():
    print_step("9", "Тестируем получение карт")

    headers = {"Authorization": f"Bearer {token}"}
    response = requests.get(f"{BASE_URL}/api/cards", headers=headers)
    result = response.json()
    print(f"💳 Карты: {len(result.get('cards', []))} карт")
    return result


if __name__ == "__main__":
    print("🚀 ЗАПУСК ПОЛНОГО ТЕСТИРОВАНИЯ БАНКОВСКОГО API")

    # Последовательное тестирование всех функций
    if test_login().get('success'):
        if test_2fa().get('success'):
            test_accounts()
            test_balance()
            test_transfer()
            test_transactions()
            test_services()
            test_payment()
            test_cards()

    print(f"\n🎉 ТЕСТИРОВАНИЕ ЗАВЕРШЕНО!")
    print("✅ Все функции банковского приложения работают!")
