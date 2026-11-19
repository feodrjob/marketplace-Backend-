-- Включение расширения pg_trgm для нечеткого поиска и ранжирования по триграммам (US-2.2 AC 1)
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

-- Создание логической схемы catalog (архитектурный паттерн Database-per-Service)
CREATE SCHEMA IF NOT EXISTS catalog;

-- Таблица категорий товаров (иерархическая структура, табы витрины)
CREATE TABLE IF NOT EXISTS catalog.categories (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id  UUID REFERENCES catalog.categories (id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(100) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_active  BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uq_categories_slug UNIQUE (slug),
    CONSTRAINT chk_categories_name_not_blank CHECK (length(trim(name)) > 0),
    CONSTRAINT chk_categories_slug_not_blank CHECK (length(trim(slug)) > 0)
);

-- Индексы для категорий
CREATE INDEX IF NOT EXISTS idx_categories_parent_id ON catalog.categories (parent_id);
CREATE INDEX IF NOT EXISTS idx_categories_sort_order ON catalog.categories (sort_order);
CREATE INDEX IF NOT EXISTS idx_categories_is_active ON catalog.categories (is_active);

-- Таблица товаров
CREATE TABLE IF NOT EXISTS catalog.products (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id     UUID NOT NULL,
    category_id   UUID NOT NULL REFERENCES catalog.categories (id) ON DELETE RESTRICT,
    title         VARCHAR(255) NOT NULL,
    description   TEXT,
    price         BIGINT NOT NULL,
    status        VARCHAR(50) NOT NULL,
    search_vector tsvector,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_products_price_positive CHECK (price > 0),
    CONSTRAINT chk_products_status CHECK (status IN ('ACTIVE', 'HIDDEN', 'ARCHIVED')),
    CONSTRAINT chk_products_title_not_blank CHECK (length(trim(title)) > 0)
);

-- Таблица изображений товаров (композитный PK product_id + media_id, порядок показа sort_order)
CREATE TABLE IF NOT EXISTS catalog.product_images (
    product_id UUID NOT NULL REFERENCES catalog.products (id) ON DELETE CASCADE,
    media_id   UUID NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (product_id, media_id)
);

-- Триггер для автоматического формирования и обновления search_vector для полнотекстового поиска на русском языке
CREATE TRIGGER trg_products_search_vector_update
    BEFORE INSERT OR UPDATE OF title, description ON catalog.products
    FOR EACH ROW
    EXECUTE FUNCTION tsvector_update_trigger(search_vector, 'pg_catalog.russian', title, description);

-- Функция и триггер для автоматического обновления updated_at при любых SQL-изменениях
CREATE OR REPLACE FUNCTION catalog.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_products_updated_at
    BEFORE UPDATE ON catalog.products
    FOR EACH ROW
    EXECUTE FUNCTION catalog.update_updated_at_column();

-- Индексы для полнотекстового и триграммного поиска
CREATE INDEX IF NOT EXISTS idx_products_search_vector ON catalog.products USING gin (search_vector);
CREATE INDEX IF NOT EXISTS idx_products_title_trgm ON catalog.products USING gin (title gin_trgm_ops);

-- Индексы для фильтрации, сортировки и пагинации каталога
CREATE INDEX IF NOT EXISTS idx_products_status ON catalog.products (status);
CREATE INDEX IF NOT EXISTS idx_products_category_id ON catalog.products (category_id);
CREATE INDEX IF NOT EXISTS idx_products_seller_id ON catalog.products (seller_id);
CREATE INDEX IF NOT EXISTS idx_products_price ON catalog.products (price);
CREATE INDEX IF NOT EXISTS idx_products_created_at ON catalog.products (created_at DESC);

-- Композитные индексы под типовые запросы витрины и личного кабинета продавца
CREATE INDEX IF NOT EXISTS idx_products_status_category_id ON catalog.products (status, category_id);
CREATE INDEX IF NOT EXISTS idx_products_seller_status ON catalog.products (seller_id, status);

-- Индекс для получения изображений товара с учетом порядка сортировки
CREATE INDEX IF NOT EXISTS idx_product_images_sort_order ON catalog.product_images (product_id, sort_order);
-- Индекс для проверки использования media_id другими товарами (US-3.1 AC 4)
CREATE INDEX IF NOT EXISTS idx_product_images_media_id ON catalog.product_images (media_id);
