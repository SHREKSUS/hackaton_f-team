"""
Модуль для шифрования номеров банковских карт
Использует Fernet (симметричное шифрование) для безопасного хранения номеров карт
"""
from cryptography.fernet import Fernet
import base64
import os

# Генерируем ключ из секретного ключа приложения или используем фиксированный для демо
# В продакшене ключ должен храниться в переменных окружения
SECRET_KEY = os.getenv('CARD_ENCRYPTION_KEY', 'bank-secret-key-2024-card-encryption-key-32bytes!!')
# Fernet требует 32 байта для ключа, поэтому хешируем секретный ключ
from hashlib import sha256
key = sha256(SECRET_KEY.encode()).digest()
fernet_key = base64.urlsafe_b64encode(key)
cipher_suite = Fernet(fernet_key)


class CardEncryption:
    """Класс для шифрования и дешифрования номеров карт"""
    
    @staticmethod
    def encrypt_card_number(card_number):
        """
        Шифрует номер карты
        
        Args:
            card_number: Номер карты в формате "1234 5678 9012 3456"
        
        Returns:
            Зашифрованная строка в формате Base64
        """
        if not card_number:
            return card_number
        
        # Если номер уже зашифрован (начинается с gAAAAAB), возвращаем как есть
        if card_number.startswith('gAAAAAB'):
            return card_number
        
        try:
            # Убираем пробелы для шифрования
            card_number_clean = card_number.replace(' ', '').replace('-', '')
            encrypted = cipher_suite.encrypt(card_number_clean.encode())
            return encrypted.decode('utf-8')
        except Exception as e:
            # В случае ошибки возвращаем исходный номер (для обратной совместимости)
            print(f"Ошибка при шифровании номера карты: {str(e)}")
            return card_number
    
    @staticmethod
    def decrypt_card_number(encrypted_card_number):
        """
        Дешифрует номер карты
        
        Args:
            encrypted_card_number: Зашифрованная строка или обычный номер карты
        
        Returns:
            Номер карты в формате "1234 5678 9012 3456"
        """
        if not encrypted_card_number:
            return encrypted_card_number
        
        # Если номер не зашифрован (не начинается с gAAAAAB), возвращаем как есть
        if not encrypted_card_number.startswith('gAAAAAB'):
            # Форматируем номер карты, если он в виде строки цифр
            card_number = encrypted_card_number.replace(' ', '').replace('-', '')
            if len(card_number) == 16 and card_number.isdigit():
                return f"{card_number[:4]} {card_number[4:8]} {card_number[8:12]} {card_number[12:16]}"
            return encrypted_card_number
        
        try:
            decrypted = cipher_suite.decrypt(encrypted_card_number.encode('utf-8'))
            card_number = decrypted.decode('utf-8')
            # Форматируем номер карты (16 цифр -> "1234 5678 9012 3456")
            if len(card_number) == 16 and card_number.isdigit():
                return f"{card_number[:4]} {card_number[4:8]} {card_number[8:12]} {card_number[12:16]}"
            return card_number
        except Exception as e:
            # В случае ошибки возвращаем исходное значение
            print(f"Ошибка при дешифровании номера карты: {str(e)}")
            return encrypted_card_number


# Создаем экземпляр для использования в других модулях
card_encryption = CardEncryption()

