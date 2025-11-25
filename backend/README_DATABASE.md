# Настройка базы данных PostgreSQL

## Быстрая настройка

### Шаг 1: Установите PostgreSQL
Если PostgreSQL еще не установлен:
- Windows: Скачайте с https://www.postgresql.org/download/windows/
- Установите PostgreSQL с паролем для пользователя `postgres`

### Шаг 2: Создайте файл .env
Скопируйте `env_template.txt` в `.env`:
```bash
# В Windows PowerShell:
copy env_template.txt .env

# В Linux/Mac:
cp env_template.txt .env
```

### Шаг 3: Настройте параметры подключения
Откройте файл `.env` и измените параметры подключения:

```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=fbank
DB_USER=postgres
DB_PASSWORD=ваш_пароль_postgres
```

**ВАЖНО:** Замените `ваш_пароль_postgres` на реальный пароль пользователя PostgreSQL.

### Шаг 4: Создайте базу данных
Запустите скрипт настройки:
```bash
cd backend
python setup_database.py
```

Или создайте базу данных вручную:
```bash
# Подключитесь к PostgreSQL
psql -U postgres

# Создайте базу данных
CREATE DATABASE fbank;

# Выйдите
\q
```

### Шаг 5: Проверьте подключение
```bash
python bank_server/check_db.py
```

Если все хорошо, вы увидите:
```
[OK] Подключение к PostgreSQL успешно!
[OK] База данных 'fbank' существует
```

### Шаг 6: Запустите сервер
```bash
python bank_server/server.py
```

## Решение проблем

### Ошибка: "Failed to connect to PostgreSQL"

**Причина:** PostgreSQL не запущен или неправильные параметры подключения.

**Решение:**
1. Убедитесь, что PostgreSQL запущен:
   - Windows: Проверьте службу "postgresql-x64-XX" в "Службы"
   - Linux: `sudo systemctl status postgresql`
   - Mac: `brew services list | grep postgresql`

2. Проверьте параметры в `.env` файле
3. Проверьте пароль пользователя PostgreSQL

### Ошибка: "database 'fbank' does not exist"

**Причина:** База данных не создана.

**Решение:**
```bash
python setup_database.py
```

### Ошибка: "password authentication failed"

**Причина:** Неправильный пароль в `.env` файле.

**Решение:**
1. Проверьте пароль пользователя PostgreSQL
2. Обновите `.env` файл с правильным паролем

### Ошибка кодировки (Unicode/UTF-8)

**Причина:** Проблемы с кодировкой в Windows PowerShell.

**Решение:**
```powershell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding = [System.Text.Encoding]::UTF8
```

Или используйте только ASCII символы в пароле.

## Проверка подключения вручную

```bash
# Подключитесь к PostgreSQL
psql -U postgres -h localhost

# Проверьте список баз данных
\l

# Подключитесь к базе данных fbank
\c fbank

# Проверьте таблицы
\dt

# Выйдите
\q
```

## Параметры подключения по умолчанию

Если файл `.env` не найден, используются значения по умолчанию:
- Host: `localhost`
- Port: `5432`
- Database: `fbank`
- User: `postgres`
- Password: `postgres`

**ВАЖНО:** Измените эти значения в production окружении!

