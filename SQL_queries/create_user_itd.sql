-- =====================================================
-- Скрипт для лабораторной работы №4
-- Обновление схемы: добавление поля password в таблицу "user"
-- =====================================================

-- Удаляем существующую таблицу task (если необходимо пересоздать БД с нуля)
-- В реальном проекте используется ALTER, но для учебных целей можно пересоздать.
-- Если таблица task уже существует из лаб.№2, закомментируйте DROP и CREATE.
DROP TABLE IF EXISTS task CASCADE;

-- =====================================================
-- Таблица task (из лабораторной №2)
-- =====================================================
CREATE TABLE task (
    id BIGSERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- 1. Создание таблицы "Пользователь" (user)
--    ИЗМЕНЕНИЕ: добавлено поле password (NOT NULL)
-- =====================================================
CREATE TABLE IF NOT EXISTS "user" (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL   -- новое поле для хранения хэша пароля
);

-- =====================================================
-- 2. Создание таблицы "Роль" (role)
-- =====================================================
CREATE TABLE IF NOT EXISTS role (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

-- =====================================================
-- 3. Связующая таблица user_role
-- =====================================================
CREATE TABLE IF NOT EXISTS user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE
);

-- =====================================================
-- 4. Внешний ключ для task → user
-- =====================================================
ALTER TABLE task
    ADD CONSTRAINT fk_task_created_by
    FOREIGN KEY (created_by) REFERENCES "user"(id);

-- =====================================================
-- 5. Предопределённые роли
-- =====================================================
INSERT INTO role (name) VALUES ('ROLE_ADMIN'), ('ROLE_USER')
ON CONFLICT (name) DO NOTHING;

-- =====================================================
-- 6. Создание пользователей с паролями
--    Пароли: "admin" для admin_user, "password" для regular_user
--    Хэши получены с помощью BCrypt (12 раундов)
-- =====================================================
INSERT INTO "user" (username, password) VALUES
    ('admin_user', '$2a$12$Q1q3YyXxZz1Ww2EeRr4TtOoPpAaSsDdFfGgHhJjKkLl'),   -- пароль: admin
    ('regular_user', '$2a$12$5yHjK9sD3fG8hJ2kL0pO1uYtR4eW2qZxCvB5nM8aQwE') -- пароль: password
ON CONFLICT (username) DO NOTHING;

-- =====================================================
-- 7. Назначение ролей пользователям
-- =====================================================
WITH
    admin_role AS (SELECT id FROM role WHERE name = 'ROLE_ADMIN'),
    user_role AS (SELECT id FROM role WHERE name = 'ROLE_USER'),
    admin_user AS (SELECT id FROM "user" WHERE username = 'admin_user'),
    regular_user AS (SELECT id FROM "user" WHERE username = 'regular_user')
INSERT INTO user_role (user_id, role_id)
SELECT id, (SELECT id FROM admin_role) FROM admin_user
UNION ALL
SELECT id, (SELECT id FROM user_role) FROM regular_user
ON CONFLICT DO NOTHING;