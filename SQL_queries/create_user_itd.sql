-- =====================================================
-- 1. Создание таблицы "Пользователь" (user)
-- =====================================================
CREATE TABLE IF NOT EXISTS "user" (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE
);

-- =====================================================
-- 2. Создание таблицы "Роль" (role)
-- =====================================================
CREATE TABLE IF NOT EXISTS role (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

-- =====================================================
-- 3. Создание связующей таблицы "user_role" (многие ко многим)
-- =====================================================
CREATE TABLE IF NOT EXISTS user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE
);

-- =====================================================
-- 4. Добавление внешнего ключа в существующую таблицу task
--    (предполагается, что таблица task уже создана скриптом из лабораторной №2)
-- =====================================================
ALTER TABLE task
    ADD CONSTRAINT fk_task_created_by
    FOREIGN KEY (created_by) REFERENCES "user"(id);

-- =====================================================
-- 5. Вставка предопределённых ролей
-- =====================================================
INSERT INTO role (name) VALUES ('ROLE_ADMIN'), ('ROLE_USER')
ON CONFLICT (name) DO NOTHING;

-- =====================================================
-- 6. Создание пользователей:
--    - admin_user с правами администратора
--    - regular_user с правами обычного пользователя
-- =====================================================
INSERT INTO "user" (username) VALUES ('admin_user'), ('regular_user')
ON CONFLICT (username) DO NOTHING;

-- =====================================================
-- 7. Назначение ролей пользователям
-- =====================================================
-- Получаем id ролей и пользователей и связываем их
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