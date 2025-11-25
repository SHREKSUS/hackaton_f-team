# Решение проблем с регистрацией

## Ошибка: "Ошибка при регистрации. Попробуйте позже"

Эта ошибка означает, что произошла неожиданная ошибка на сервере. Следуйте инструкциям ниже для диагностики.

### Шаг 1: Проверьте, что сервер запущен

Откройте терминал в папке `backend` и запустите:
```powershell
python bank_server\server.py
```

Вы должны увидеть:
```
Banking server (PostgreSQL) starting...
API available at: http://localhost:5000
Database: PostgreSQL
```

### Шаг 2: Проверьте подключение к базе данных

В другом терминале выполните:
```powershell
python bank_server\check_db.py
```

Должно быть:
```
[OK] Подключение к PostgreSQL успешно!
[OK] База данных 'fbank' существует
```

Если есть ошибки:
1. Убедитесь, что PostgreSQL запущен
2. Проверьте файл `.env` в папке `backend`
3. Запустите `python setup_database.py` для создания БД

### Шаг 3: Проверьте логи сервера

Когда вы пытаетесь зарегистрироваться, смотрите на вывод сервера. Вы должны увидеть:
```
[INFO] Registration attempt: name=..., phone/email=..., is_phone=True
[INFO] User created with ID: ...
[INFO] Account created: KZ...
```

Если видите ошибки:
- `[ERROR] Database connection error` - проблема с подключением к БД
- `[ERROR] PostgreSQL error` - ошибка в запросе к БД
- `[ERROR] Registration error` - другая ошибка (смотрите traceback)

### Шаг 4: Проверьте формат телефона

Сервер ожидает:
- 11 цифр, начинающихся с 7 (например: 77771234567)
- 10 цифр (тогда добавляется 7 в начало)
- 11 цифр, начинающихся с 8 (конвертируется в 7)

### Шаг 5: Тест через API напрямую

Запустите тестовый скрипт:
```powershell
python test_registration.py
```

Это покажет точную ошибку, если она есть.

## Частые проблемы

### Проблема: База данных не создана

**Решение:**
```powershell
python setup_database.py
```

### Проблема: Неправильный пароль PostgreSQL

**Решение:**
1. Откройте `backend\.env`
2. Измените `DB_PASSWORD` на правильный пароль
3. Перезапустите сервер

### Проблема: PostgreSQL не запущен

**Решение:**
1. Откройте "Службы" Windows
2. Найдите службу PostgreSQL
3. Запустите её, если она остановлена

### Проблема: Порт 5000 занят

**Решение:**
Измените порт в `server.py`:
```python
app.run(debug=True, host='0.0.0.0', port=5001)  # Используйте другой порт
```

И обновите `RetrofitClient.kt` в Android приложении.

## Получение подробных логов

Для более детальной диагностики проверьте:
1. Логи сервера в консоли
2. Логи Android приложения в Logcat (фильтр: `UserRepository`)
3. Ответ сервера в Network Inspector Android Studio

## Если ничего не помогает

1. Проверьте, что все зависимости установлены:
   ```powershell
   pip install -r requirements.txt
   ```

2. Убедитесь, что используется правильная версия Python (3.8+)

3. Попробуйте пересоздать базу данных:
   ```powershell
   psql -U postgres
   DROP DATABASE fbank;
   CREATE DATABASE fbank;
   \q
   python setup_database.py
   ```

