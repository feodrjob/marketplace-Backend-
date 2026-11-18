-- Включение расширения pg_trgm для нечеткого поиска и ранжирования по триграммам (US-2.2 AC 1)
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

-- Создание логической схемы catalog (архитектурный паттерн Database-per-Service)
CREATE SCHEMA IF NOT EXISTS catalog;

-- Таблица товаров
CREATE TABLE IF NOT EXISTS catalog.products (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id     UUID NOT NULL,
    title         VARCHAR(255) NOT NULL,
    category      VARCHAR(255) NOT NULL,
    description   TEXT,
    price         BIGINT NOT NULL,
    status        VARCHAR(50) NOT NULL,
    search_vector tsvector,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_products_price_positive CHECK (price > 0),
    CONSTRAINT chk_products_status CHECK (status IN ('ACTIVE', 'HIDDEN', 'ARCHIVED')),
    CONSTRAINT chk_products_title_not_blank CHECK (length(trim(title)) > 0),
    CONSTRAINT chk_products_category_not_blank CHECK (length(trim(category)) > 0)
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

-- Индексы для полнотекстового и триграммного поиска
CREATE INDEX IF NOT EXISTS idx_products_search_vector ON catalog.products USING gin (search_vector);
CREATE INDEX IF NOT EXISTS idx_products_title_trgm ON catalog.products USING gin (title gin_trgm_ops);

-- Индексы для фильтрации, сортировки и пагинации каталога
CREATE INDEX IF NOT EXISTS idx_products_status ON catalog.products (status);
CREATE INDEX IF NOT EXISTS idx_products_category ON catalog.products (category);
CREATE INDEX IF NOT EXISTS idx_products_seller_id ON catalog.products (seller_id);
CREATE INDEX IF NOT EXISTS idx_products_price ON catalog.products (price);
CREATE INDEX IF NOT EXISTS idx_products_created_at ON catalog.products (created_at DESC);

-- Индекс для получения изображений товара с учетом порядка сортировки
CREATE INDEX IF NOT EXISTS idx_product_images_sort_order ON catalog.product_images (product_id, sort_order);
