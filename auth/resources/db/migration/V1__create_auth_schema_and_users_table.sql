-- Создание логической схемы auth (архитектурный паттерн Database-per-Service)
CREATE SCHEMA IF NOT EXISTS auth;

-- Таблица пользователей микросервиса авторизации auth.users
CREATE TABLE IF NOT EXISTS auth.users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(32)  NOT NULL,

    -- Реквизиты покупателя (role = 'BUYER')
    full_name     VARCHAR(255),
    phone         VARCHAR(50),

    -- Реквизиты продавца (role = 'SELLER')
    company_name  VARCHAR(255),
    inn           VARCHAR(12) UNIQUE,
    description   TEXT,

    -- Реквизиты администратора (role = 'ADMIN')
    department    VARCHAR(255),

    -- Аудит создания и обновления
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Ограничение ролей: строго BUYER, SELLER или ADMIN
    CONSTRAINT chk_users_role
        CHECK (role IN ('BUYER', 'SELLER', 'ADMIN')),

    -- US-1.2: Валидация формата ИНН — ровно 10 или 12 цифр
    CONSTRAINT chk_users_inn_format
        CHECK (inn IS NULL OR inn ~ '^[0-9]{10}$|^[0-9]{12}$'),

    -- Инвариант продавца: обязательное указание компании и ИНН
    CONSTRAINT chk_users_seller_required_fields
        CHECK (role != 'SELLER' OR (company_name IS NOT NULL AND inn IS NOT NULL))
);
