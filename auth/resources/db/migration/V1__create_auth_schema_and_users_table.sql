-- Создание логической схемы auth (архитектурный паттерн Database-per-Service)
CREATE SCHEMA IF NOT EXISTS auth;

-- Нативный перечисляемый тип для ролей пользователей
CREATE TYPE auth.user_role AS ENUM ('BUYER', 'SELLER', 'ADMIN');

-- Таблица базовых учетных записей пользователей
CREATE TABLE IF NOT EXISTS auth.users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          auth.user_role NOT NULL,
    department    VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Таблица профилей покупателей
CREATE TABLE IF NOT EXISTS auth.buyer_profiles (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID UNIQUE NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    full_name  VARCHAR(255),
    phone      VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Таблица профилей продавцов
CREATE TABLE IF NOT EXISTS auth.seller_profiles (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID UNIQUE NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    company_name VARCHAR(255) NOT NULL,
    inn          VARCHAR(12) UNIQUE NOT NULL,
    description  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
