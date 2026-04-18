-- Полная очистка данных todo-приложения (без DROP SCHEMA).
-- Подходит для PostgreSQL.
-- Запускать в БД, где таблицы: task, "user", role, user_role.

TRUNCATE TABLE task RESTART IDENTITY CASCADE;
TRUNCATE TABLE user_role RESTART IDENTITY CASCADE;
TRUNCATE TABLE role RESTART IDENTITY CASCADE;
TRUNCATE TABLE "user" RESTART IDENTITY CASCADE;

