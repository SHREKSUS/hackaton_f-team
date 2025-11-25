from flask import Flask, jsonify, request
from flask_cors import CORS
import jwt
import datetime
import bcrypt
import sys
import os
import psycopg2
import psycopg2.errors
from dotenv import load_dotenv

# Загружаем переменные окружения из .env файла
# Ищем .env файл в родительской директории (backend/)
backend_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
env_path = os.path.join(backend_dir, '.env')
if os.path.exists(env_path):
    load_dotenv(env_path)
    print(f"[INFO] Загружены переменные окружения из {env_path}")
else:
    print(f"[WARNING] Файл .env не найден в {backend_dir}")
    print("[INFO] Используются значения по умолчанию из database.py")

# Добавляем родительскую директорию в путь для импорта
sys.path.insert(0, backend_dir)
from bank_server.database import db
from bank_server.card_encryption import card_encryption

app = Flask(__name__)
app.config['SECRET_KEY'] = 'bank-secret-key-2024'
CORS(app, resources={r"/api/*": {"origins": "*", "methods": ["GET", "POST", "PUT", "DELETE", "OPTIONS"]}})

# Временное хранилище для 2FA кодов
twofa_codes = {}

# Вспомогательные функции


def get_user_from_token():
    token = request.headers.get('Authorization')
    if not token:
        print("[DEBUG] No Authorization header found")
        return None
    
    # Проверяем формат токена (должен быть "Bearer <token>")
    if not token.startswith('Bearer '):
        print(f"[DEBUG] Invalid token format: {token[:20]}...")
        return None
    
    try:
        token_value = token.split(' ')[1]
        decoded = jwt.decode(token_value, app.config['SECRET_KEY'], algorithms=['HS256'])
        return decoded.get('phone')
    except jwt.ExpiredSignatureError:
        print("[DEBUG] Token expired")
        return None
    except jwt.InvalidTokenError as e:
        print(f"[DEBUG] Invalid token: {e}")
        return None
    except Exception as e:
        print(f"[DEBUG] Error decoding token: {type(e).__name__}: {e}")
        return None


def get_user_id(phone):
    with db.get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT id FROM users WHERE phone = %s", (phone,))
        result = cursor.fetchone()
        return result[0] if result else None

# ==================== АУТЕНТИФИКАЦИЯ ====================


@app.route('/')
def home():
    return jsonify({
        "endpoints": {
            "health_check": "/api/health",
            "pronunciation_check": "/api/check_pronunciation"
        },
        "message": "Backend API is running. Use frontend to access the application."
    })


@app.route('/api/auth/register', methods=['POST', 'OPTIONS'])
def register():
    if request.method == 'OPTIONS':
        # Обработка preflight запроса для CORS
        response = jsonify({})
        response.headers.add('Access-Control-Allow-Origin', '*')
        response.headers.add('Access-Control-Allow-Headers', 'Content-Type,Authorization')
        response.headers.add('Access-Control-Allow-Methods', 'POST, OPTIONS')
        return response
    try:
        data = request.get_json()
        if not data:
            return jsonify({
                "success": False,
                "message": "Отсутствуют данные запроса"
            }), 400
        
        name = data.get('name', '').strip()
        # Поддерживаем как 'phone', так и 'email' для обратной совместимости
        email_or_phone = data.get('phone', '').strip() or data.get('email', '').strip()
        password = data.get('password', '').strip()
        
        if not name or not password:
            return jsonify({
                "success": False,
                "message": "Имя и пароль обязательны"
            }), 400
        
        if not email_or_phone:
            return jsonify({
                "success": False,
                "message": "Телефон или email обязательны"
            }), 400
        
        # Определяем, это email или телефон
        is_phone = not '@' in email_or_phone and email_or_phone.replace('+', '').replace('-', '').replace(' ', '').replace('(', '').replace(')', '').isdigit()
        
        phone_clean = None  # Инициализируем переменную
        
        print(f"[INFO] Registration attempt: name={name}, phone/email={email_or_phone}, is_phone={is_phone}")
        
        with db.get_connection() as conn:
            cursor = conn.cursor()
            
            # Проверяем существование пользователя
            if is_phone:
                # Очищаем телефон от форматирования
                phone_clean = ''.join(filter(str.isdigit, email_or_phone))
                if len(phone_clean) == 11 and phone_clean.startswith('7'):
                    phone_clean = phone_clean
                elif len(phone_clean) == 10:
                    phone_clean = '7' + phone_clean
                elif len(phone_clean) == 11 and phone_clean.startswith('8'):
                    # Поддержка формата 8XXXXXXXXXX -> 7XXXXXXXXXX
                    phone_clean = '7' + phone_clean[1:]
                else:
                    return jsonify({
                        "success": False,
                        "message": f"Неверный формат телефона. Получено {len(phone_clean)} цифр, ожидается 10 или 11 цифр (начинающихся с 7 или 8)"
                    }), 400
                
                cursor.execute("SELECT id FROM users WHERE phone = %s", (phone_clean,))
                if cursor.fetchone():
                    return jsonify({
                        "success": False,
                        "message": "Пользователь с таким телефоном уже существует"
                    }), 400
                
                # Хешируем пароль с помощью bcrypt перед сохранением
                password_bytes = password.encode('utf-8')
                salt = bcrypt.gensalt()
                password_hash = bcrypt.hashpw(password_bytes, salt).decode('utf-8')
                
                # Создаем пользователя с телефоном
                cursor.execute(
                    "INSERT INTO users (name, phone, password) VALUES (%s, %s, %s) RETURNING id",
                    (name, phone_clean, password_hash)
                )
                result = cursor.fetchone()
                if not result:
                    return jsonify({
                        "success": False,
                        "message": "Ошибка при создании пользователя"
                    }), 500
                user_id = result[0]
            else:
                # Проверяем email
                if not email_or_phone or '@' not in email_or_phone:
                    return jsonify({
                        "success": False,
                        "message": "Неверный формат email"
                    }), 400
                
                cursor.execute("SELECT id FROM users WHERE phone = %s", (email_or_phone,))
                if cursor.fetchone():
                    return jsonify({
                        "success": False,
                        "message": "Пользователь с таким email уже существует"
                    }), 400
                
                # Хешируем пароль с помощью bcrypt перед сохранением
                password_bytes = password.encode('utf-8')
                salt = bcrypt.gensalt()
                password_hash = bcrypt.hashpw(password_bytes, salt).decode('utf-8')
                
                # Создаем пользователя с email (сохраняем в поле phone для совместимости)
                cursor.execute(
                    "INSERT INTO users (name, phone, password) VALUES (%s, %s, %s) RETURNING id",
                    (name, email_or_phone, password_hash)
                )
                result = cursor.fetchone()
                if not result:
                    return jsonify({
                        "success": False,
                        "message": "Ошибка при создании пользователя"
                    }), 500
                user_id = result[0]
            print(f"[INFO] User created with ID: {user_id}")
            
            # Создаем начальный счет для пользователя
            account_number = f"KZ{str(user_id).zfill(9)}"
            cursor.execute(
                "INSERT INTO accounts (user_id, balance, currency, name, number) VALUES (%s, %s, %s, %s, %s)",
                (user_id, 0.0, 'KZT', 'Основной счет', account_number)
            )
            print(f"[INFO] Account created: {account_number}")
        
        # Создаем JWT токен для нового пользователя
        phone_or_email = phone_clean if is_phone else email_or_phone
        token_payload = {
            'phone': phone_or_email,
            'name': name,
            'user_id': user_id,
            'exp': datetime.datetime.utcnow() + datetime.timedelta(days=7)  # 7 дней вместо 24 часов
        }
        token = jwt.encode(token_payload, app.config['SECRET_KEY'], algorithm='HS256')
        
        return jsonify({
            "success": True,
            "message": "Регистрация успешна",
            "userId": user_id,
            "token": token
        }), 201
    except psycopg2.OperationalError as e:
        # Ошибка подключения к БД
        import traceback
        error_msg = str(e).encode('utf-8', errors='replace').decode('utf-8')
        print(f"[ERROR] Database connection error: {error_msg}")
        print(traceback.format_exc())
        
        return jsonify({
            "success": False,
            "message": "Не удалось подключиться к базе данных. Проверьте, что PostgreSQL запущен и настройки подключения правильные."
        }), 500
        
    except psycopg2.IntegrityError as e:
        # Ошибка целостности данных (дубликат и т.д.)
        import traceback
        error_msg = str(e).encode('utf-8', errors='replace').decode('utf-8')
        print(f"[ERROR] Database integrity error: {error_msg}")
        print(traceback.format_exc())
        
        if 'duplicate' in error_msg.lower() or 'unique' in error_msg.lower():
            return jsonify({
                "success": False,
                "message": "Пользователь с такими данными уже существует"
            }), 400
        else:
            return jsonify({
                "success": False,
                "message": "Ошибка сохранения данных. Проверьте введенные данные."
            }), 400
            
    except psycopg2.Error as e:
        # Другие ошибки PostgreSQL
        import traceback
        error_msg = str(e).encode('utf-8', errors='replace').decode('utf-8')
        print(f"[ERROR] PostgreSQL error: {error_msg}")
        print(traceback.format_exc())
        
        return jsonify({
            "success": False,
            "message": "Ошибка базы данных. Убедитесь, что база данных настроена правильно."
        }), 500
        
    except (UnicodeDecodeError, UnicodeError, UnicodeEncodeError) as e:
        # Ошибки кодировки
        import traceback
        error_msg = str(e).encode('utf-8', errors='replace').decode('utf-8')
        print(f"[ERROR] Encoding error: {error_msg}")
        print(traceback.format_exc())
        
        return jsonify({
            "success": False,
            "message": "Ошибка обработки данных. Используйте только стандартные символы."
        }), 400
        
    except Exception as e:
        # Все остальные ошибки
        import traceback
        error_msg = str(e).encode('utf-8', errors='replace').decode('utf-8')
        error_type = type(e).__name__
        print(f"[ERROR] Registration error ({error_type}): {error_msg}")
        print(traceback.format_exc())
        
        # Более детальное сообщение для отладки
        if 'connection' in error_msg.lower() or 'connect' in error_msg.lower():
            user_message = "Не удалось подключиться к базе данных. Убедитесь, что PostgreSQL запущен."
        elif 'table' in error_msg.lower() and 'does not exist' in error_msg.lower():
            user_message = "База данных не настроена. Запустите setup_database.py для создания таблиц."
        elif 'password' in error_msg.lower() and 'authentication' in error_msg.lower():
            user_message = "Ошибка аутентификации в базе данных. Проверьте настройки подключения в .env файле."
        else:
            user_message = f"Ошибка при регистрации: {error_msg[:100]}"
        
        return jsonify({
            "success": False,
            "message": user_message
        }), 500


@app.route('/api/auth/login', methods=['POST'])
def login():
    data = request.get_json()
    # Поддерживаем как email, так и phone в поле email
    email_or_phone = data.get('email', '').strip() or data.get('phone', '').strip()
    password = data.get('password', '').strip()
    
    if not email_or_phone or not password:
        return jsonify({"success": False, "message": "Email/телефон и пароль обязательны"}), 400

    # Нормализуем номер телефона: убираем все нецифровые символы
    phone_clean = ''.join(filter(str.isdigit, email_or_phone))
    
    # Нормализуем номер телефона так же, как при регистрации
    if len(phone_clean) == 11 and phone_clean.startswith('7'):
        phone_clean = phone_clean
    elif len(phone_clean) == 10:
        phone_clean = '7' + phone_clean
    elif len(phone_clean) == 11 and phone_clean.startswith('8'):
        # Поддержка формата 8XXXXXXXXXX -> 7XXXXXXXXXX
        phone_clean = '7' + phone_clean[1:]
    # Если это не телефон (email), оставляем как есть
    elif '@' in email_or_phone:
        phone_clean = email_or_phone

    with db.get_connection() as conn:
        cursor = conn.cursor()
        # Ищем пользователя по нормализованному номеру телефона
        cursor.execute(
            "SELECT id, name, phone, password FROM users WHERE phone = %s", 
            (phone_clean,)
        )
        user = cursor.fetchone()
        
        # Если не нашли, пробуем найти по исходному номеру
        if not user:
            cursor.execute(
                "SELECT id, name, phone, password FROM users WHERE phone = %s", 
                (email_or_phone,)
            )
            user = cursor.fetchone()

    if not user:
        return jsonify({"success": False, "message": "Неверный email/телефон или пароль"}), 401
    
    user_id, name, stored_phone, stored_password_hash = user
    
    # Проверяем пароль с помощью bcrypt
    try:
        password_bytes = password.encode('utf-8')
        
        # Проверяем, что хеш пароля является строкой
        if isinstance(stored_password_hash, bytes):
            stored_password_hash_bytes = stored_password_hash
        else:
            stored_password_hash_bytes = stored_password_hash.encode('utf-8')
        
        # Проверяем, что хеш начинается с правильного префикса bcrypt
        if not stored_password_hash_bytes.startswith(b'$2b$') and not stored_password_hash_bytes.startswith(b'$2a$') and not stored_password_hash_bytes.startswith(b'$2y$'):
            return jsonify({"success": False, "message": "Ошибка формата пароля в базе данных"}), 500
        
        password_match = bcrypt.checkpw(password_bytes, stored_password_hash_bytes)
        
        if not password_match:
            return jsonify({"success": False, "message": "Неверный email/телефон или пароль"}), 401
    except Exception as e:
        return jsonify({"success": False, "message": "Ошибка проверки пароля"}), 500
    
    # Создаем JWT токен напрямую (без 2FA для упрощения)
    token_payload = {
        'phone': stored_phone,
        'name': name,
        'user_id': user_id,
        'exp': datetime.datetime.utcnow() + datetime.timedelta(days=7)  # 7 дней вместо 24 часов
    }

    token = jwt.encode(
        token_payload, app.config['SECRET_KEY'], algorithm='HS256')

    return jsonify({
        "success": True,
        "token": token,
        "user": {
            "id": user_id,
            "name": name,
            "email": stored_phone  # Может быть телефоном или email
        },
        "message": "Успешный вход!"
    })


@app.route('/api/auth/save-pin', methods=['POST'])
def save_pin():
    """Сохранение PIN-кода пользователя"""
    data = request.get_json()
    user_id = data.get('userId')
    pin = data.get('pin', '').strip()
    
    if not user_id or not pin:
        return jsonify({
            "success": False,
            "message": "userId и pin обязательны"
        }), 400
    
    if len(pin) != 4 or not pin.isdigit():
        return jsonify({
            "success": False,
            "message": "PIN должен состоять из 4 цифр"
        }), 400
    
    try:
        # Хешируем PIN с помощью bcrypt (более безопасно, чем SHA-256)
        pin_bytes = pin.encode('utf-8')
        salt = bcrypt.gensalt()
        pin_hash = bcrypt.hashpw(pin_bytes, salt).decode('utf-8')
        
        with db.get_connection() as conn:
            cursor = conn.cursor()
            # Проверяем существование пользователя
            cursor.execute("SELECT id FROM users WHERE id = %s", (user_id,))
            if not cursor.fetchone():
                return jsonify({
                    "success": False,
                    "message": "Пользователь не найден"
                }), 404
            
            # Сохраняем хеш PIN
            cursor.execute(
                "UPDATE users SET pin_hash = %s WHERE id = %s",
                (pin_hash, user_id)
            )
        
        return jsonify({
            "success": True,
            "message": "PIN успешно сохранен"
        })
    except Exception as e:
        return jsonify({
            "success": False,
            "message": f"Ошибка при сохранении PIN: {str(e)}"
        }), 500


@app.route('/api/auth/verify-pin', methods=['POST'])
def verify_pin():
    """Проверка PIN-кода пользователя"""
    data = request.get_json()
    user_id = data.get('userId')
    pin = data.get('pin', '').strip()
    
    if not user_id or not pin:
        return jsonify({
            "success": False,
            "message": "userId и pin обязательны"
        }), 400
    
    try:
        with db.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT pin_hash FROM users WHERE id = %s", (user_id,))
            result = cursor.fetchone()
            
            if not result:
                return jsonify({
                    "success": False,
                    "message": "Пользователь не найден"
                }), 404
            
            pin_hash = result[0]
            
            if not pin_hash:
                return jsonify({
                    "success": False,
                    "message": "PIN не установлен"
                }), 400
            
            # Проверяем PIN
            pin_bytes = pin.encode('utf-8')
            pin_hash_bytes = pin_hash.encode('utf-8')
            
            if bcrypt.checkpw(pin_bytes, pin_hash_bytes):
                return jsonify({
                    "success": True,
                    "message": "PIN верный"
                })
            else:
                return jsonify({
                    "success": False,
                    "message": "Неверный PIN"
                }), 401
                
    except Exception as e:
        return jsonify({
            "success": False,
            "message": f"Ошибка при проверке PIN: {str(e)}"
        }), 500


@app.route('/api/auth/verify-2fa', methods=['POST'])
def verify_2fa():
    data = request.get_json()
    # Поддерживаем как phone, так и email
    phone_or_email = data.get('phone') or data.get('email', '')
    code = data.get('code')

    if not phone_or_email or phone_or_email not in twofa_codes or twofa_codes[phone_or_email] != code:
        return jsonify({"success": False, "message": "Неверный код подтверждения"}), 401

    with db.get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT id, name, phone FROM users WHERE phone = %s", (phone_or_email,))
        user = cursor.fetchone()

    if not user:
        return jsonify({"success": False, "message": "Пользователь не найден"}), 401

    user_id, name, stored_phone = user

    token_payload = {
        'phone': stored_phone,  # Используем сохраненное значение из БД
        'name': name,
        'user_id': user_id,
        'exp': datetime.datetime.utcnow() + datetime.timedelta(days=7)  # 7 дней вместо 24 часов
    }

    token = jwt.encode(
        token_payload, app.config['SECRET_KEY'], algorithm='HS256')
    del twofa_codes[phone_or_email]

    return jsonify({
        "success": True,
        "token": token,
        "user": {
            "id": user_id,
            "name": name,
            "email": stored_phone  # Может быть телефоном или email
        },
        "message": "Успешный вход!"
    })

# ==================== СЧЕТА И БАЛАНС ====================


@app.route('/api/accounts', methods=['GET'])
def get_accounts():
    phone = get_user_from_token()
    if not phone:
        return jsonify({"error": "Неверный токен"}), 401

    user_id = get_user_id(phone)
    with db.get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute(
            "SELECT id, balance, currency, name, number FROM accounts WHERE user_id = %s", (user_id,))
        accounts = [{"id": row[0], "balance": float(row[1]), "currency": row[2],
                     "name": row[3], "number": row[4]} for row in cursor.fetchall()]

    return jsonify({"success": True, "accounts": accounts})


@app.route('/api/balance', methods=['GET'])
def get_balance():
    phone = get_user_from_token()
    if not phone:
        return jsonify({"error": "Неверный токен"}), 401

    user_id = get_user_id(phone)
    with db.get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute(
            "SELECT SUM(balance) FROM accounts WHERE user_id = %s", (user_id,))
        result = cursor.fetchone()
        total_balance = float(result[0]) if result[0] else 0.0

    return jsonify({
        "success": True,
        "total_balance": total_balance,
        "currency": "KZT"
    })

# ==================== ПЕРЕВОДЫ ====================


@app.route('/api/transfer', methods=['POST'])
def transfer():
    phone = get_user_from_token()
    if not phone:
        return jsonify({"error": "Неверный токен"}), 401

    data = request.get_json()
    from_account = data.get('from_account')
    to_account = data.get('to_account')
    amount = float(data.get('amount'))
    description = data.get('description', '')

    user_id = get_user_id(phone)
    with db.get_connection() as conn:
        cursor = conn.cursor()

        # Проверяем счет и баланс
        cursor.execute(
            "SELECT id, balance FROM accounts WHERE number = %s AND user_id = %s", (from_account, user_id))
        from_acc = cursor.fetchone()

        if not from_acc:
            return jsonify({"success": False, "message": "Счет отправителя не найден"}), 400

        if float(from_acc[1]) < amount:
            return jsonify({"success": False, "message": "Недостаточно средств"}), 400

        # Обновляем баланс
        cursor.execute(
            "UPDATE accounts SET balance = balance - %s WHERE number = %s", (amount, from_account))

        # Добавляем транзакцию и получаем ID
        cursor.execute(
            "INSERT INTO transactions (user_id, amount, type, description, from_account, to_account) VALUES (%s, %s, %s, %s, %s, %s) RETURNING id",
            (user_id, -amount, 'transfer',
             description or f"Перевод на {to_account}", from_account, to_account)
        )
        transaction_id = cursor.fetchone()[0]

        # Получаем новый баланс
        cursor.execute(
            "SELECT balance FROM accounts WHERE number = %s", (from_account,))
        new_balance = float(cursor.fetchone()[0])

    return jsonify({
        "success": True,
        "message": "Перевод выполнен успешно",
        "new_balance": new_balance,
        "transaction_id": transaction_id
    })

# ==================== ИСТОРИЯ ОПЕРАЦИЙ ====================


@app.route('/api/transactions', methods=['GET'])
def get_transactions():
    phone = get_user_from_token()
    if not phone:
        return jsonify({"error": "Неверный токен"}), 401

    user_id = get_user_id(phone)
    with db.get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute('''
            SELECT amount, type, description, from_account, to_account, created_at 
            FROM transactions 
            WHERE user_id = %s 
            ORDER BY created_at DESC
        ''', (user_id,))

        transactions_list = []
        for row in cursor.fetchall():
            transactions_list.append({
                "amount": float(row[0]),
                "type": row[1],
                "description": row[2],
                "from_account": row[3],
                "to_account": row[4],
                "date": row[5].isoformat() if row[5] else None
            })

    return jsonify({"success": True, "transactions": transactions_list})

# ==================== ПЛАТЕЖИ И УСЛУГИ ====================


@app.route('/api/services', methods=['GET'])
def get_services():
    services = [
        {"id": 1, "name": "Казахтелеком", "category": "Интернет"},
        {"id": 2, "name": "KEGOC", "category": "Электричество"},
        {"id": 3, "name": "АО СК", "category": "Коммунальные"},
        {"id": 4, "name": "Beeline", "category": "Мобильная связь"}
    ]
    return jsonify({"success": True, "services": services})


@app.route('/api/payment', methods=['POST'])
def make_payment():
    phone = get_user_from_token()
    if not phone:
        return jsonify({"error": "Неверный токен"}), 401

    data = request.get_json()
    account = data.get('account')
    service = data.get('service')
    amount = float(data.get('amount'))

    user_id = get_user_id(phone)
    with db.get_connection() as conn:
        cursor = conn.cursor()

        # Проверяем счет и баланс
        cursor.execute(
            "SELECT balance FROM accounts WHERE number = %s AND user_id = %s", (account, user_id))
        acc_balance = cursor.fetchone()

        if not acc_balance:
            return jsonify({"success": False, "message": "Счет не найден"}), 400

        if float(acc_balance[0]) < amount:
            return jsonify({"success": False, "message": "Недостаточно средств"}), 400

        # Обновляем баланс
        cursor.execute(
            "UPDATE accounts SET balance = balance - %s WHERE number = %s", (amount, account))

        # Добавляем транзакцию
        cursor.execute(
            "INSERT INTO transactions (user_id, amount, type, description, from_account, to_account) VALUES (%s, %s, %s, %s, %s, %s)",
            (user_id, -amount, 'payment', f"Оплата: {service}", account, service)
        )

        # Получаем новый баланс
        cursor.execute("SELECT balance FROM accounts WHERE number = %s", (account,))
        new_balance = float(cursor.fetchone()[0])

    return jsonify({
        "success": True,
        "message": "Платеж выполнен успешно",
        "new_balance": new_balance
    })

# ==================== КАРТЫ ====================


@app.route('/api/cards', methods=['GET'])
def get_cards():
    phone = get_user_from_token()
    if not phone:
        return jsonify({"error": "Неверный токен"}), 401

    user_id = get_user_id(phone)
    with db.get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute(
            "SELECT id, number, type, balance, currency, expiry FROM cards WHERE user_id = %s", (user_id,))

        cards = []
        for row in cursor.fetchall():
            # Дешифруем номер карты перед отправкой клиенту
            # Метод decrypt_card_number автоматически определяет, зашифрован ли номер
            # row[0] = id, row[1] = number (encrypted), row[2] = type, row[3] = balance, row[4] = currency, row[5] = expiry
            decrypted_number = card_encryption.decrypt_card_number(row[1])
            
            cards.append({
                "id": row[0],  # ID карты
                "number": decrypted_number,
                "type": row[2],
                "balance": float(row[3]),
                "currency": row[4],
                "expiry": row[5]
            })

    return jsonify({"success": True, "cards": cards})


@app.route('/api/cards/transfer', methods=['POST'])
def transfer_between_cards():
    """Перевод между картами пользователя"""
    print(f"[DEBUG] Received request to /api/cards/transfer")
    print(f"[DEBUG] Request method: {request.method}")
    print(f"[DEBUG] Request path: {request.path}")
    print(f"[DEBUG] Request headers: {dict(request.headers)}")
    
    phone = get_user_from_token()
    print(f"[DEBUG] Phone from token: {phone}")
    if not phone:
        print(f"[DEBUG] Invalid token")
        return jsonify({"success": False, "message": "Неверный токен"}), 401
    
    data = request.get_json()
    print(f"[DEBUG] Request data: {data}")
    from_card_id = data.get('fromCardId')
    to_card_id = data.get('toCardId')
    amount = float(data.get('amount', 0))
    description = data.get('description', '')
    
    print(f"[DEBUG] Parsed: from_card_id={from_card_id}, to_card_id={to_card_id}, amount={amount}")
    
    if not from_card_id or not to_card_id:
        print(f"[DEBUG] Missing card IDs")
        return jsonify({"success": False, "message": "Не указаны карты"}), 400
    
    if amount <= 0:
        print(f"[DEBUG] Invalid amount: {amount}")
        return jsonify({"success": False, "message": "Сумма должна быть больше нуля"}), 400
    
    if from_card_id == to_card_id:
        print(f"[DEBUG] Same card IDs")
        return jsonify({"success": False, "message": "Нельзя перевести на ту же карту"}), 400
    
    user_id = get_user_id(phone)
    print(f"[DEBUG] User ID: {user_id}")
    if not user_id:
        print(f"[DEBUG] User not found for phone: {phone}")
        return jsonify({"success": False, "message": "Пользователь не найден"}), 404
    
    try:
        print(f"[DEBUG] Starting database operations")
        with db.get_connection() as conn:
            cursor = conn.cursor()
            
            # Проверяем, что обе карты принадлежат пользователю
            # Сначала пробуем найти по ID
            print(f"[DEBUG] Checking from_card: id={from_card_id}, user_id={user_id}")
            cursor.execute(
                "SELECT id, balance, number FROM cards WHERE id = %s AND user_id = %s",
                (from_card_id, user_id)
            )
            from_card = cursor.fetchone()
            print(f"[DEBUG] From card result by ID: {from_card}")
            
            # Если не найдено по ID, получаем все карты пользователя и ищем по последним 4 цифрам
            if not from_card:
                print(f"[DEBUG] Card not found by ID, listing all user cards:")
                cursor.execute(
                    "SELECT id, number, balance FROM cards WHERE user_id = %s",
                    (user_id,)
                )
                all_cards = cursor.fetchall()
                for card in all_cards:
                    decrypted = card_encryption.decrypt_card_number(card[1])
                    print(f"[DEBUG]   Card ID={card[0]}, number={decrypted}, balance={card[2]}")
                
                # Пробуем найти карту по последним 4 цифрам из запроса (если они есть в данных)
                # Но так как у нас нет номера в запросе, просто возвращаем ошибку с информацией
                print(f"[DEBUG] From card not found by ID. Available card IDs: {[c[0] for c in all_cards]}")
                return jsonify({
                    "success": False, 
                    "message": f"Карта отправителя не найдена (ID: {from_card_id}). Доступные ID карт: {[c[0] for c in all_cards]}"
                }), 404
            
            print(f"[DEBUG] Checking to_card: id={to_card_id}, user_id={user_id}")
            cursor.execute(
                "SELECT id, balance FROM cards WHERE id = %s AND user_id = %s",
                (to_card_id, user_id)
            )
            to_card = cursor.fetchone()
            print(f"[DEBUG] To card result: {to_card}")
            
            if not to_card:
                print(f"[DEBUG] To card not found")
                return jsonify({"success": False, "message": "Карта получателя не найдена"}), 404
            
            # Проверяем баланс
            from_balance = float(from_card[1])
            print(f"[DEBUG] From balance: {from_balance}, Amount: {amount}")
            if from_balance < amount:
                print(f"[DEBUG] Insufficient funds")
                return jsonify({"success": False, "message": "Недостаточно средств"}), 400
            
            # Выполняем перевод
            print(f"[DEBUG] Executing transfer")
            cursor.execute(
                "UPDATE cards SET balance = balance - %s WHERE id = %s",
                (amount, from_card_id)
            )
            
            cursor.execute(
                "UPDATE cards SET balance = balance + %s WHERE id = %s",
                (amount, to_card_id)
            )
            
            # Получаем новый баланс карты отправителя
            cursor.execute("SELECT balance FROM cards WHERE id = %s", (from_card_id,))
            new_balance = float(cursor.fetchone()[0])
            print(f"[DEBUG] New balance: {new_balance}")
            
            # Получаем номера карт для записи в транзакцию
            cursor.execute("SELECT number FROM cards WHERE id = %s", (from_card_id,))
            from_card_number_encrypted = cursor.fetchone()[0]
            from_card_number = card_encryption.decrypt_card_number(from_card_number_encrypted)
            
            cursor.execute("SELECT number FROM cards WHERE id = %s", (to_card_id,))
            to_card_number_encrypted = cursor.fetchone()[0]
            to_card_number = card_encryption.decrypt_card_number(to_card_number_encrypted)
            
            # Создаем запись о транзакции с номерами карт вместо ID
            print(f"[DEBUG] Creating transaction record")
            print(f"[DEBUG] From card number: {from_card_number}, To card number: {to_card_number}")
            cursor.execute(
                """INSERT INTO transactions (user_id, amount, type, description, from_account, to_account) 
                   VALUES (%s, %s, %s, %s, %s, %s) RETURNING id""",
                (user_id, -amount, 'transfer', description or f"Перевод между картами", 
                 from_card_number, to_card_number)
            )
            transaction_id = cursor.fetchone()[0]
            print(f"[DEBUG] Transaction created with id: {transaction_id}")
        
        print(f"[DEBUG] Returning success response")
        return jsonify({
            "success": True,
            "message": "Перевод выполнен успешно",
            "newBalance": new_balance,
            "transactionId": transaction_id
        })
    except Exception as e:
        import traceback
        print(f"[ERROR] Ошибка при переводе между картами: {str(e)}")
        print(traceback.format_exc())
        return jsonify({
            "success": False,
            "message": f"Ошибка при переводе: {str(e)}"
        }), 500


@app.route('/api/cards/transfer-by-phone', methods=['POST'])
def transfer_by_phone():
    """Перевод по номеру телефона"""
    print(f"[DEBUG] Received request to /api/cards/transfer-by-phone")
    print(f"[DEBUG] Request method: {request.method}")
    print(f"[DEBUG] Request path: {request.path}")
    
    phone = get_user_from_token()
    print(f"[DEBUG] Phone from token: {phone}")
    if not phone:
        print(f"[DEBUG] Invalid token")
        return jsonify({"success": False, "message": "Неверный токен"}), 401
    
    data = request.get_json()
    print(f"[DEBUG] Request data: {data}")
    from_card_id = data.get('fromCardId')
    recipient_phone = data.get('phone', '').strip()
    amount = float(data.get('amount', 0))
    description = data.get('description', '')
    
    print(f"[DEBUG] Parsed: from_card_id={from_card_id}, recipient_phone={recipient_phone}, amount={amount}")
    
    if not from_card_id:
        print(f"[DEBUG] Missing from card ID")
        return jsonify({"success": False, "message": "Не указана карта отправителя"}), 400
    
    if not recipient_phone:
        print(f"[DEBUG] Missing recipient phone")
        return jsonify({"success": False, "message": "Не указан номер телефона получателя"}), 400
    
    if amount <= 0:
        print(f"[DEBUG] Invalid amount: {amount}")
        return jsonify({"success": False, "message": "Сумма должна быть больше нуля"}), 400
    
    # Очищаем и нормализуем номер телефона получателя
    phone_clean = ''.join(filter(str.isdigit, recipient_phone))
    if len(phone_clean) == 11 and phone_clean.startswith('7'):
        phone_clean = phone_clean
    elif len(phone_clean) == 10:
        phone_clean = '7' + phone_clean
    elif len(phone_clean) == 11 and phone_clean.startswith('8'):
        phone_clean = '7' + phone_clean[1:]
    else:
        return jsonify({"success": False, "message": "Неверный формат номера телефона получателя"}), 400
    
    user_id = get_user_id(phone)
    print(f"[DEBUG] User ID: {user_id}")
    if not user_id:
        print(f"[DEBUG] User not found for phone: {phone}")
        return jsonify({"success": False, "message": "Пользователь не найден"}), 404
    
    try:
        print(f"[DEBUG] Starting database operations")
        with db.get_connection() as conn:
            cursor = conn.cursor()
            
            # Проверяем карту отправителя
            print(f"[DEBUG] Checking from_card: id={from_card_id}, user_id={user_id}")
            cursor.execute(
                "SELECT id, balance, number FROM cards WHERE id = %s AND user_id = %s",
                (from_card_id, user_id)
            )
            from_card = cursor.fetchone()
            print(f"[DEBUG] From card result: {from_card}")
            
            if not from_card:
                print(f"[DEBUG] From card not found")
                return jsonify({"success": False, "message": "Карта отправителя не найдена"}), 404
            
            # Проверяем баланс
            from_balance = float(from_card[1])
            print(f"[DEBUG] From balance: {from_balance}, Amount: {amount}")
            if from_balance < amount:
                print(f"[DEBUG] Insufficient funds")
                return jsonify({"success": False, "message": "Недостаточно средств"}), 400
            
            # Находим пользователя получателя по номеру телефона
            print(f"[DEBUG] Finding recipient user by phone: {phone_clean}")
            cursor.execute("SELECT id FROM users WHERE phone = %s", (phone_clean,))
            recipient_user = cursor.fetchone()
            
            if not recipient_user:
                print(f"[DEBUG] Recipient user not found")
                return jsonify({"success": False, "message": "Пользователь с таким номером телефона не найден"}), 404
            
            recipient_user_id = recipient_user[0]
            print(f"[DEBUG] Recipient user ID: {recipient_user_id}")
            
            # Проверяем, что не переводим самому себе
            if recipient_user_id == user_id:
                print(f"[DEBUG] Cannot transfer to self")
                return jsonify({"success": False, "message": "Нельзя перевести самому себе"}), 400
            
            # Находим карту получателя (берем первую доступную карту пользователя)
            print(f"[DEBUG] Finding recipient card")
            cursor.execute(
                "SELECT id, balance, number FROM cards WHERE user_id = %s LIMIT 1",
                (recipient_user_id,)
            )
            to_card = cursor.fetchone()
            
            if not to_card:
                print(f"[DEBUG] Recipient has no cards")
                return jsonify({"success": False, "message": "У получателя нет карт"}), 404
            
            to_card_id = to_card[0]
            to_card_balance_before = float(to_card[1])
            print(f"[DEBUG] To card ID: {to_card_id}, Balance before: {to_card_balance_before}")
            
            # Выполняем перевод
            print(f"[DEBUG] Executing transfer")
            print(f"[DEBUG] Updating from_card (id={from_card_id}): balance -{amount}")
            cursor.execute(
                "UPDATE cards SET balance = balance - %s WHERE id = %s",
                (amount, from_card_id)
            )
            rows_updated_from = cursor.rowcount
            print(f"[DEBUG] Rows updated (from_card): {rows_updated_from}")
            
            print(f"[DEBUG] Updating to_card (id={to_card_id}): balance +{amount}")
            cursor.execute(
                "UPDATE cards SET balance = balance + %s WHERE id = %s",
                (amount, to_card_id)
            )
            rows_updated_to = cursor.rowcount
            print(f"[DEBUG] Rows updated (to_card): {rows_updated_to}")
            
            # Получаем новый баланс карты отправителя
            cursor.execute("SELECT balance FROM cards WHERE id = %s", (from_card_id,))
            new_balance = float(cursor.fetchone()[0])
            print(f"[DEBUG] New balance (from_card): {new_balance}")
            
            # Проверяем новый баланс карты получателя
            cursor.execute("SELECT balance FROM cards WHERE id = %s", (to_card_id,))
            recipient_new_balance = float(cursor.fetchone()[0])
            expected_balance = to_card_balance_before + amount
            print(f"[DEBUG] New balance (to_card): {recipient_new_balance}, Expected: {expected_balance}")
            if abs(recipient_new_balance - expected_balance) > 0.01:
                print(f"[ERROR] Balance mismatch! Expected {expected_balance}, got {recipient_new_balance}")
            
            # Получаем номера карт для записи в транзакцию
            cursor.execute("SELECT number FROM cards WHERE id = %s", (from_card_id,))
            from_card_number_encrypted = cursor.fetchone()[0]
            from_card_number = card_encryption.decrypt_card_number(from_card_number_encrypted)
            
            cursor.execute("SELECT number FROM cards WHERE id = %s", (to_card_id,))
            to_card_number_encrypted = cursor.fetchone()[0]
            to_card_number = card_encryption.decrypt_card_number(to_card_number_encrypted)
            
            # Создаем запись о транзакции
            print(f"[DEBUG] Creating transaction record")
            print(f"[DEBUG] From card number: {from_card_number}, To card number: {to_card_number}")
            cursor.execute(
                """INSERT INTO transactions (user_id, amount, type, description, from_account, to_account) 
                   VALUES (%s, %s, %s, %s, %s, %s) RETURNING id""",
                (user_id, -amount, 'transfer', description or f"Перевод по номеру телефона {phone_clean}", 
                 from_card_number, to_card_number)
            )
            transaction_id = cursor.fetchone()[0]
            print(f"[DEBUG] Transaction created with id: {transaction_id}")
            
            # Также создаем транзакцию для получателя (положительную)
            # Получаем номер телефона отправителя для описания
            cursor.execute("SELECT phone FROM users WHERE id = %s", (user_id,))
            sender_phone_result = cursor.fetchone()
            sender_phone = sender_phone_result[0] if sender_phone_result else phone
            
            cursor.execute(
                """INSERT INTO transactions (user_id, amount, type, description, from_account, to_account) 
                   VALUES (%s, %s, %s, %s, %s, %s) RETURNING id""",
                (recipient_user_id, amount, 'transfer', description or f"Перевод по номеру телефона от {sender_phone}", 
                 from_card_number, to_card_number)
            )
            recipient_transaction_id = cursor.fetchone()[0]
            print(f"[DEBUG] Recipient transaction created with id: {recipient_transaction_id}")
            
            # Явно коммитим транзакцию для гарантии сохранения изменений
            conn.commit()
            print(f"[DEBUG] Transaction committed successfully")
            
            # Проверяем финальные балансы после коммита
            cursor.execute("SELECT balance FROM cards WHERE id = %s", (from_card_id,))
            final_from_balance = float(cursor.fetchone()[0])
            cursor.execute("SELECT balance FROM cards WHERE id = %s", (to_card_id,))
            final_to_balance = float(cursor.fetchone()[0])
            print(f"[DEBUG] Final balances - From card: {final_from_balance}, To card: {final_to_balance}")
        
        print(f"[DEBUG] Returning success response")
        return jsonify({
            "success": True,
            "message": "Перевод выполнен успешно",
            "newBalance": new_balance,
            "transactionId": transaction_id
        })
    except ValueError as e:
        print(f"[ERROR] ValueError: {str(e)}")
        return jsonify({
            "success": False,
            "message": f"Неверный формат данных: {str(e)}"
        }), 400
    except Exception as e:
        import traceback
        print(f"[ERROR] Ошибка при переводе по номеру телефона: {str(e)}")
        print(traceback.format_exc())
        return jsonify({
            "success": False,
            "message": f"Ошибка при переводе: {str(e)}"
        }), 500


@app.route('/api/cards/transfer-to-other-bank', methods=['POST'])
def transfer_to_other_bank():
    """Перевод в другой банк по номеру карты"""
    print(f"[DEBUG] Received request to /api/cards/transfer-to-other-bank")
    print(f"[DEBUG] Request method: {request.method}")
    print(f"[DEBUG] Request path: {request.path}")
    
    phone = get_user_from_token()
    print(f"[DEBUG] Phone from token: {phone}")
    if not phone:
        print(f"[DEBUG] Invalid token")
        return jsonify({"success": False, "message": "Неверный токен"}), 401
    
    data = request.get_json()
    print(f"[DEBUG] Request data: {data}")
    from_card_id = data.get('fromCardId')
    to_card_number = data.get('toCardNumber', '').strip().replace(" ", "").replace("-", "")
    amount = float(data.get('amount', 0))
    description = data.get('description', '')
    
    print(f"[DEBUG] Parsed: from_card_id={from_card_id}, to_card_number={to_card_number}, amount={amount}")
    
    if not from_card_id:
        print(f"[DEBUG] Missing from card ID")
        return jsonify({"success": False, "message": "Не указана карта отправителя"}), 400
    
    if not to_card_number:
        print(f"[DEBUG] Missing recipient card number")
        return jsonify({"success": False, "message": "Не указан номер карты получателя"}), 400
    
    if len(to_card_number) != 16 or not to_card_number.isdigit():
        print(f"[DEBUG] Invalid card number format")
        return jsonify({"success": False, "message": "Неверный формат номера карты (должно быть 16 цифр)"}), 400
    
    if amount <= 0:
        print(f"[DEBUG] Invalid amount: {amount}")
        return jsonify({"success": False, "message": "Сумма должна быть больше нуля"}), 400
    
    user_id = get_user_id(phone)
    print(f"[DEBUG] User ID: {user_id}")
    if not user_id:
        print(f"[DEBUG] User not found for phone: {phone}")
        return jsonify({"success": False, "message": "Пользователь не найден"}), 404
    
    try:
        print(f"[DEBUG] Starting database operations")
        with db.get_connection() as conn:
            cursor = conn.cursor()
            
            # Проверяем карту отправителя
            print(f"[DEBUG] Checking from_card: id={from_card_id}, user_id={user_id}")
            cursor.execute(
                "SELECT id, balance, number FROM cards WHERE id = %s AND user_id = %s",
                (from_card_id, user_id)
            )
            from_card = cursor.fetchone()
            print(f"[DEBUG] From card result: {from_card}")
            
            if not from_card:
                print(f"[DEBUG] From card not found")
                return jsonify({"success": False, "message": "Карта отправителя не найдена"}), 404
            
            # Проверяем баланс
            from_balance = float(from_card[1])
            print(f"[DEBUG] From balance: {from_balance}, Amount: {amount}")
            if from_balance < amount:
                print(f"[DEBUG] Insufficient funds")
                return jsonify({"success": False, "message": "Недостаточно средств"}), 400
            
            # Проверяем, что карта получателя не принадлежит текущему пользователю
            # (перевод в другой банк означает перевод на карту, которой нет в нашей системе)
            cursor.execute(
                "SELECT id FROM cards WHERE number = %s AND user_id = %s",
                (to_card_number, user_id)
            )
            own_card = cursor.fetchone()
            if own_card:
                print(f"[DEBUG] Cannot transfer to own card")
                return jsonify({"success": False, "message": "Для перевода на свою карту используйте перевод между картами"}), 400
            
            # Выполняем перевод (списываем средства с карты отправителя)
            print(f"[DEBUG] Executing transfer")
            print(f"[DEBUG] Updating from_card (id={from_card_id}): balance -{amount}")
            cursor.execute(
                "UPDATE cards SET balance = balance - %s WHERE id = %s",
                (amount, from_card_id)
            )
            rows_updated_from = cursor.rowcount
            print(f"[DEBUG] Rows updated (from_card): {rows_updated_from}")
            
            # Получаем новый баланс карты отправителя
            cursor.execute("SELECT balance FROM cards WHERE id = %s", (from_card_id,))
            new_balance = float(cursor.fetchone()[0])
            print(f"[DEBUG] New balance (from_card): {new_balance}")
            
            # Получаем номер карты отправителя для записи в транзакцию
            cursor.execute("SELECT number FROM cards WHERE id = %s", (from_card_id,))
            from_card_number_encrypted = cursor.fetchone()[0]
            from_card_number = card_encryption.decrypt_card_number(from_card_number_encrypted)
            
            # Форматируем номер карты получателя для отображения (последние 4 цифры)
            to_card_display = f"**** **** **** {to_card_number[-4:]}"
            
            # Создаем запись о транзакции
            print(f"[DEBUG] Creating transaction record")
            print(f"[DEBUG] From card number: {from_card_number}, To card number: {to_card_display}")
            cursor.execute(
                """INSERT INTO transactions (user_id, amount, type, description, from_account, to_account) 
                   VALUES (%s, %s, %s, %s, %s, %s) RETURNING id""",
                (user_id, -amount, 'transfer_other_bank', description or f"Перевод в другой банк на карту {to_card_display}", 
                 from_card_number, to_card_display)
            )
            transaction_id = cursor.fetchone()[0]
            print(f"[DEBUG] Transaction created with id: {transaction_id}")
            
            # Явно коммитим транзакцию для гарантии сохранения изменений
            conn.commit()
            print(f"[DEBUG] Transaction committed successfully")
            
            # Проверяем финальный баланс после коммита
            cursor.execute("SELECT balance FROM cards WHERE id = %s", (from_card_id,))
            final_from_balance = float(cursor.fetchone()[0])
            print(f"[DEBUG] Final balance (from_card): {final_from_balance}")
        
        print(f"[DEBUG] Returning success response")
        return jsonify({
            "success": True,
            "message": "Перевод в другой банк выполнен успешно",
            "newBalance": new_balance,
            "transactionId": transaction_id
        })
    except ValueError as e:
        print(f"[ERROR] ValueError: {str(e)}")
        return jsonify({
            "success": False,
            "message": f"Неверный формат данных: {str(e)}"
        }), 400
    except Exception as e:
        import traceback
        print(f"[ERROR] Ошибка при переводе в другой банк: {str(e)}")
        print(traceback.format_exc())
        return jsonify({
            "success": False,
            "message": f"Ошибка при переводе: {str(e)}"
        }), 500


@app.route('/api/cards/international-transfer', methods=['POST'])
def international_transfer():
    """Международный перевод (SWIFT)"""
    print(f"[DEBUG] Received request to /api/cards/international-transfer")
    print(f"[DEBUG] Request method: {request.method}")
    print(f"[DEBUG] Request path: {request.path}")
    
    phone = get_user_from_token()
    print(f"[DEBUG] Phone from token: {phone}")
    if not phone:
        print(f"[DEBUG] Invalid token")
        return jsonify({"success": False, "message": "Неверный токен"}), 401
    
    data = request.get_json()
    print(f"[DEBUG] Request data: {data}")
    from_card_id = data.get('fromCardId')
    transfer_system = data.get('transferSystem', 'SWIFT').strip()
    recipient_name = data.get('recipientName', '').strip()
    swift_code = data.get('swiftCode', '').strip().upper() if data.get('swiftCode') else None
    iban = data.get('iban', '').strip().upper() if data.get('iban') else None
    receiver_phone = data.get('receiverPhone', '').strip() if data.get('receiverPhone') else None
    country = data.get('country', '').strip()
    amount = float(data.get('amount', 0))
    currency = data.get('currency', 'USD').upper()
    description = data.get('description', '')
    
    print(f"[DEBUG] Parsed: from_card_id={from_card_id}, transfer_system={transfer_system}, recipient_name={recipient_name}, swift_code={swift_code}, iban={iban}, receiver_phone={receiver_phone}, country={country}, amount={amount}, currency={currency}")
    
    if not from_card_id:
        print(f"[DEBUG] Missing from card ID")
        return jsonify({"success": False, "message": "Не указана карта отправителя"}), 400
    
    if transfer_system not in ['SWIFT', 'Western Union', 'Korona Pay']:
        print(f"[DEBUG] Invalid transfer system: {transfer_system}")
        return jsonify({"success": False, "message": "Неподдерживаемая система перевода"}), 400
    
    if not recipient_name:
        print(f"[DEBUG] Missing recipient name")
        return jsonify({"success": False, "message": "Не указано имя получателя"}), 400
    
    # Валидация в зависимости от системы перевода
    if transfer_system == 'SWIFT':
        if not swift_code or len(swift_code) < 8 or len(swift_code) > 11:
            print(f"[DEBUG] Invalid SWIFT code")
            return jsonify({"success": False, "message": "Неверный формат SWIFT кода (8-11 символов)"}), 400
        
        if not iban or len(iban) < 15 or len(iban) > 34:
            print(f"[DEBUG] Invalid IBAN")
            return jsonify({"success": False, "message": "Неверный формат IBAN (15-34 символа)"}), 400
    elif transfer_system in ['Western Union', 'Korona Pay']:
        if not receiver_phone:
            print(f"[DEBUG] Missing receiver phone")
            return jsonify({"success": False, "message": "Не указан номер телефона получателя"}), 400
        phone_clean = ''.join(filter(str.isdigit, receiver_phone))
        if len(phone_clean) < 10:
            print(f"[DEBUG] Invalid receiver phone")
            return jsonify({"success": False, "message": "Неверный формат номера телефона получателя"}), 400
    
    if not country:
        print(f"[DEBUG] Missing country")
        return jsonify({"success": False, "message": "Не указана страна получателя"}), 400
    
    if amount <= 0:
        print(f"[DEBUG] Invalid amount: {amount}")
        return jsonify({"success": False, "message": "Сумма должна быть больше нуля"}), 400
    
    if currency not in ['USD', 'EUR', 'GBP', 'RUB', 'CNY', 'JPY', 'KZT']:
        print(f"[DEBUG] Invalid currency: {currency}")
        return jsonify({"success": False, "message": "Неподдерживаемая валюта"}), 400
    
    user_id = get_user_id(phone)
    print(f"[DEBUG] User ID: {user_id}")
    if not user_id:
        print(f"[DEBUG] User not found for phone: {phone}")
        return jsonify({"success": False, "message": "Пользователь не найден"}), 404
    
    try:
        print(f"[DEBUG] Starting database operations")
        with db.get_connection() as conn:
            cursor = conn.cursor()
            
            # Проверяем карту отправителя
            print(f"[DEBUG] Checking from_card: id={from_card_id}, user_id={user_id}")
            cursor.execute(
                "SELECT id, balance, number FROM cards WHERE id = %s AND user_id = %s",
                (from_card_id, user_id)
            )
            from_card = cursor.fetchone()
            print(f"[DEBUG] From card result: {from_card}")
            
            if not from_card:
                print(f"[DEBUG] From card not found")
                return jsonify({"success": False, "message": "Карта отправителя не найдена"}), 404
            
            # Проверяем баланс
            from_balance = float(from_card[1])
            print(f"[DEBUG] From balance: {from_balance}, Amount: {amount}")
            if from_balance < amount:
                print(f"[DEBUG] Insufficient funds")
                return jsonify({"success": False, "message": "Недостаточно средств"}), 400
            
            # Имитация международного перевода - списываем средства с карты отправителя
            print(f"[DEBUG] Executing international transfer")
            cursor.execute(
                "UPDATE cards SET balance = balance - %s WHERE id = %s",
                (amount, from_card_id)
            )
            
            # Получаем новый баланс карты отправителя
            cursor.execute("SELECT balance FROM cards WHERE id = %s", (from_card_id,))
            new_balance = float(cursor.fetchone()[0])
            print(f"[DEBUG] New balance (sender): {new_balance}")
            
            # Получаем номер карты для записи в транзакцию
            cursor.execute("SELECT number FROM cards WHERE id = %s", (from_card_id,))
            from_card_number_encrypted = cursor.fetchone()[0]
            from_card_number = card_encryption.decrypt_card_number(from_card_number_encrypted)
            
            # Создаем запись о транзакции для отправителя
            print(f"[DEBUG] Creating international transaction record")
            transfer_description = description or f"Международный перевод {currency} через {transfer_system} в {country}"
            
            # Формируем to_account в зависимости от системы
            to_account = ""
            if transfer_system == 'SWIFT' and iban:
                to_account = f"{iban} ({country})"
            elif transfer_system in ['Western Union', 'Korona Pay'] and receiver_phone:
                to_account = f"{receiver_phone} ({country})"
            else:
                to_account = f"{recipient_name} ({country})"
            
            cursor.execute(
                """INSERT INTO transactions (user_id, amount, type, description, from_account, to_account) 
                   VALUES (%s, %s, %s, %s, %s, %s) RETURNING id""",
                (user_id, -amount, 'international_transfer', transfer_description, 
                 from_card_number, to_account)
            )
            transaction_id = cursor.fetchone()[0]
            print(f"[DEBUG] International transaction created with id: {transaction_id}")
            
            # Явно коммитим транзакцию для гарантии сохранения изменений
            conn.commit()
            print(f"[DEBUG] Transaction committed successfully")
            
            # Проверяем финальный баланс после коммита
            cursor.execute("SELECT balance FROM cards WHERE id = %s", (from_card_id,))
            final_from_balance = float(cursor.fetchone()[0])
            print(f"[DEBUG] Final balance (from_card): {final_from_balance}")
        
        print(f"[DEBUG] Returning success response")
        return jsonify({
            "success": True,
            "message": f"Международный перевод {currency} через {transfer_system} выполнен успешно",
            "newBalance": new_balance,
            "transactionId": transaction_id
        })
    except ValueError as e:
        print(f"[ERROR] ValueError: {str(e)}")
        return jsonify({
            "success": False,
            "message": f"Неверный формат данных: {str(e)}"
        }), 400
    except Exception as e:
        import traceback
        print(f"[ERROR] Ошибка при международном переводе: {str(e)}")
        print(traceback.format_exc())
        return jsonify({
            "success": False,
            "message": f"Ошибка при переводе: {str(e)}"
        }), 500


@app.route('/api/cards/create', methods=['POST'])
def create_card():
    """Создание новой карты для пользователя"""
    phone = get_user_from_token()
    if not phone:
        return jsonify({"success": False, "message": "Неверный токен"}), 401
    
    data = request.get_json()
    card_type = data.get('type', 'debit')  # debit или credit
    
    user_id = get_user_id(phone)
    if not user_id:
        return jsonify({"success": False, "message": "Пользователь не найден"}), 404
    
    try:
        with db.get_connection() as conn:
            cursor = conn.cursor()
            
            # Генерируем номер карты (16 цифр)
            import random
            card_number = ''.join([str(random.randint(0, 9)) for _ in range(16)])
            formatted_number = f"{card_number[:4]} {card_number[4:8]} {card_number[8:12]} {card_number[12:16]}"
            
            # Шифруем номер карты перед сохранением в БД
            encrypted_number = card_encryption.encrypt_card_number(formatted_number)
            
            # Генерируем срок действия (MM/YY, через 3 года)
            from datetime import datetime, timedelta
            expiry_date = (datetime.now() + timedelta(days=3*365)).strftime("%m/%y")
            
            # Получаем баланс с основного счета или создаем счет если его нет
            cursor.execute("SELECT balance FROM accounts WHERE user_id = %s LIMIT 1", (user_id,))
            account_result = cursor.fetchone()
            
            if account_result is None:
                # Счета нет - создаем его
                import uuid
                account_number = str(uuid.uuid4()).replace('-', '')[:16]
                cursor.execute(
                    """INSERT INTO accounts (user_id, balance, currency, name, number) 
                       VALUES (%s, %s, %s, %s, %s)""",
                    (user_id, 0.0, 'KZT', 'Основной счет', account_number)
                )
                balance = 0.0
            else:
                balance = float(account_result[0]) if account_result[0] is not None else 0.0
            
            # Создаем карту (номер сохраняется в зашифрованном виде)
            cursor.execute(
                """INSERT INTO cards (user_id, number, type, balance, currency, expiry) 
                   VALUES (%s, %s, %s, %s, %s, %s) RETURNING id""",
                (user_id, encrypted_number, card_type, balance, 'KZT', expiry_date)
            )
            card_id = cursor.fetchone()[0]
        
        return jsonify({
            "success": True,
            "message": "Карта успешно создана",
            "card": {
                "id": card_id,
                "number": formatted_number,  # Отправляем расшифрованный номер клиенту
                "type": card_type,
                "balance": balance,
                "currency": "KZT",
                "expiry": expiry_date
            }
        }), 201
    except Exception as e:
        import traceback
        error_trace = traceback.format_exc()
        print(f"Ошибка при создании карты: {str(e)}")
        print(f"Traceback: {error_trace}")
        return jsonify({
            "success": False,
            "message": f"Ошибка при создании карты: {str(e)}",
            "error": error_trace
        }), 500


@app.route('/api/cards/deposit', methods=['POST'])
def deposit_to_card():
    """Пополнение карты"""
    phone = get_user_from_token()
    if not phone:
        auth_header = request.headers.get('Authorization', '')
        if not auth_header:
            return jsonify({"success": False, "message": "Токен не предоставлен"}), 401
        elif not auth_header.startswith('Bearer '):
            return jsonify({"success": False, "message": "Неверный формат токена"}), 401
        else:
            return jsonify({"success": False, "message": "Токен истек или неверен. Пожалуйста, войдите снова"}), 401
    
    data = request.get_json()
    card_id = data.get('cardId')
    amount = float(data.get('amount', 0))
    
    if not card_id:
        return jsonify({"success": False, "message": "Не указана карта"}), 400
    
    if amount <= 0:
        return jsonify({"success": False, "message": "Сумма должна быть больше нуля"}), 400
    
    user_id = get_user_id(phone)
    if not user_id:
        return jsonify({"success": False, "message": "Пользователь не найден"}), 404
    
    try:
        with db.get_connection() as conn:
            cursor = conn.cursor()
            
            # Проверяем, что карта принадлежит пользователю
            cursor.execute(
                "SELECT id, balance FROM cards WHERE id = %s AND user_id = %s",
                (card_id, user_id)
            )
            card = cursor.fetchone()
            
            if not card:
                return jsonify({"success": False, "message": "Карта не найдена"}), 404
            
            current_balance = float(card[1]) if card[1] is not None else 0.0
            new_balance = current_balance + amount
            
            # Обновляем баланс карты
            cursor.execute(
                "UPDATE cards SET balance = %s WHERE id = %s AND user_id = %s",
                (new_balance, card_id, user_id)
            )
            
            # Обновляем баланс счета (если есть)
            cursor.execute("SELECT id FROM accounts WHERE user_id = %s LIMIT 1", (user_id,))
            account = cursor.fetchone()
            if account:
                cursor.execute(
                    "UPDATE accounts SET balance = balance + %s WHERE user_id = %s",
                    (amount, user_id)
                )
            
            # Получаем номер карты для транзакции
            cursor.execute(
                "SELECT number FROM cards WHERE id = %s AND user_id = %s",
                (card_id, user_id)
            )
            card_result = cursor.fetchone()
            if not card_result:
                return jsonify({"success": False, "message": "Карта не найдена"}), 404
            
            # Расшифровываем номер карты для отображения в транзакции
            encrypted_card_number = card_result[0]
            try:
                decrypted_card_number = card_encryption.decrypt_card_number(encrypted_card_number)
            except:
                # Если не удалось расшифровать, используем зашифрованный номер
                decrypted_card_number = encrypted_card_number
            
            # Создаем транзакцию (используем структуру как в других транзакциях)
            cursor.execute(
                """INSERT INTO transactions (user_id, amount, type, description, from_account, to_account)
                   VALUES (%s, %s, %s, %s, %s, %s) RETURNING id""",
                (user_id, amount, 'deposit', 'Пополнение счета', decrypted_card_number, decrypted_card_number)
            )
            transaction_id = cursor.fetchone()[0]
            
            conn.commit()
            
            return jsonify({
                "success": True,
                "message": "Счет успешно пополнен",
                "newBalance": new_balance,
                "transactionId": transaction_id
            }), 200
    except Exception as e:
        import traceback
        error_trace = traceback.format_exc()
        print(f"Ошибка при пополнении карты: {str(e)}")
        print(f"Traceback: {error_trace}")
        return jsonify({
            "success": False,
            "message": f"Ошибка при пополнении карты: {str(e)}"
        }), 500


@app.route('/api/cards/check', methods=['GET'])
def check_cards():
    """Проверка наличия карт у пользователя"""
    phone = get_user_from_token()
    if not phone:
        return jsonify({"success": False, "hasCards": False}), 401
    
    user_id = get_user_id(phone)
    if not user_id:
        return jsonify({"success": False, "hasCards": False}), 404
    
    try:
        with db.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT COUNT(*) FROM cards WHERE user_id = %s", (user_id,))
            count = cursor.fetchone()[0]
        
        return jsonify({
            "success": True,
            "hasCards": count > 0,
            "count": count
        })
    except Exception as e:
        return jsonify({
            "success": False,
            "hasCards": False,
            "message": str(e)
        }), 500


@app.route('/api/users/by-phone', methods=['POST'])
def get_user_by_phone():
    """Получение информации о пользователе по номеру телефона"""
    phone = get_user_from_token()
    if not phone:
        return jsonify({"success": False, "message": "Неверный токен"}), 401
    
    data = request.get_json()
    if not data:
        return jsonify({"success": False, "message": "Отсутствуют данные запроса"}), 400
    
    recipient_phone = data.get('phone', '').strip()
    if not recipient_phone:
        return jsonify({"success": False, "message": "Номер телефона не указан"}), 400
    
    # Очищаем номер телефона от форматирования
    phone_clean = ''.join(filter(str.isdigit, recipient_phone))
    if len(phone_clean) == 11 and phone_clean.startswith('7'):
        phone_clean = phone_clean
    elif len(phone_clean) == 10:
        phone_clean = '7' + phone_clean
    elif len(phone_clean) == 11 and phone_clean.startswith('8'):
        phone_clean = '7' + phone_clean[1:]
    else:
        return jsonify({"success": False, "message": "Неверный формат номера телефона"}), 400
    
    try:
        with db.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT id, name, phone FROM users WHERE phone = %s", (phone_clean,))
            user = cursor.fetchone()
        
        if not user:
            return jsonify({
                "success": False,
                "message": "Пользователь с таким номером телефона не найден"
            }), 404
        
        user_id, name, stored_phone = user
        return jsonify({
            "success": True,
            "user": {
                "id": user_id,
                "name": name,
                "phone": stored_phone
            }
        })
    except Exception as e:
        import traceback
        print(f"[ERROR] Ошибка при получении пользователя по номеру: {str(e)}")
        print(traceback.format_exc())
        return jsonify({
            "success": False,
            "message": f"Ошибка при получении информации о пользователе: {str(e)}"
        }), 500


if __name__ == '__main__':
    print("Banking server (PostgreSQL) starting...")
    print("API available at: http://localhost:5000")
    print("Database: PostgreSQL")
    print("\nTest data:")
    print("   Телефон: 77071234567")
    print("   Пароль: test123")
    print("   2FA код: 1234")
    
    # Выводим все зарегистрированные маршруты для отладки
    print("\nRegistered routes:")
    for rule in app.url_map.iter_rules():
        methods_str = ', '.join(sorted(rule.methods))
        print(f"  {rule.rule} [{methods_str}]")
    
    # Проверяем конкретно маршрут регистрации
    register_rule = None
    for rule in app.url_map.iter_rules():
        if rule.rule == '/api/auth/register':
            register_rule = rule
            break
    if register_rule:
        print(f"\n[DEBUG] Register route found: {register_rule.rule} with methods: {sorted(register_rule.methods)}")
    else:
        print("\n[ERROR] Register route NOT FOUND!")
    
    app.run(debug=True, host='0.0.0.0', port=5000)
