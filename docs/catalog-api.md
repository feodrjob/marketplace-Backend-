# Публичный каталог US-2.1

Catalog связывает HTTP-контроллеры, PublicCatalogService и JPA-адаптеры.
DAO преобразует сущности PostgreSQL в доменные модели внутри транзакции,
включая лениво загружаемые изображения. Контроллеры возвращают отдельные DTO.

## Маршруты Catalog

Все три GET-маршрута доступны гостю без JWT:

| Маршрут | Результат |
|---|---|
| /api/v1/catalog/categories | Дерево видимых категорий |
| /api/v1/catalog/products | Список публичных карточек товаров |
| /api/v1/catalog/products/{id} | Одна публичная карточка |

У категорий есть параметры `tree=true|false` (по умолчанию true) и
`parentId=<UUID>`. При заданном parentId возвращаются его потомки,
сам родитель в ответ не включается. В плоском ответе children пустой.

На витрину попадают только ACTIVE-товары в активной ветке категорий.
Неизвестные и недоступные товары/категории дают 404.
Некорректные UUID и значения tree дают 400.
Ошибки используют Content-Type application/problem+json и поля
type, title, status, detail, instance, timestamp.

Карточка содержит id, title, description, price, categoryId, categoryName,
categorySlug, images, sellerId, companyName, sellerDescription, createdAt,
updatedAt. Цена — целое число бонусов, даты — строки ISO 8601.
Каждое изображение содержит mediaId и sortOrder. URL изображения получает
клиент через описанный в общей документации GET /api/v1/media/{mediaId}.

Поиск, фильтры, сортировка и пагинация US-2.2 реализованы для GET /api/v1/catalog/products через Elasticsearch. Ответ содержит items, page, size, total вместо массива карточек.

## Новый контракт Auth для магазина

GET /api/v1/sellers/{sellerId}/shop доступен без JWT.
sellerId — идентификатор аккаунта auth.users, совпадающий с sub JWT,
а не идентификатор строки auth.seller_profiles.

Пример ответа 200:

    {
      "sellerId": "77b85be3-7d6d-48de-8aeb-d54f4019f587",
      "companyName": "Магазин электроники",
      "description": null
    }

Ответ содержит ровно три публичных поля. Некорректный UUID даёт 400,
неизвестный аккаунт или роль, отличная от SELLER, — 404.
Описание OpenAPI находится в auth/resources/static/openapi/auth.yaml.
Этот новый маршрут необходимо отразить и в отдельном репозитории marketplace-docs.

Catalog преобразует 404 Auth в отсутствие магазина: товар остаётся доступным,
companyName и sellerDescription равны null. Таймаут, другой ошибочный статус,
пустой/некорректный ответ или несовпадающий sellerId приводят к 503 Catalog.
Catalog не читает таблицы Auth напрямую и не использует JWT гостя для этого GET.
Текущий сервис получает магазин для каждой карточки; пакетная загрузка/кэширование
могут быть добавлены отдельно при развитии списка товаров.

## Настройки и запуск

| Переменная окружения Catalog | По умолчанию |
|---|---|
| CATALOG_AUTH_BASE_URL | http://localhost:8081 |
| CATALOG_AUTH_CONNECT_TIMEOUT | 2s |
| CATALOG_AUTH_READ_TIMEOUT | 3s |

В Compose адрес Auth переопределён на http://auth:8081.
Для запуска скопируйте .env.example в .env, задайте POSTGRES_PASSWORD
и выполните из корня репозитория:

    docker compose up --build --wait

По умолчанию Auth доступен на http://localhost:8080,
Catalog — на http://localhost:8083. Публичные методы Catalog можно вызывать
без заголовка Authorization. Создание товаров в API US-2.1 не входит:
для ручной проверки нужны заранее подготовленные категории и товары в БД.

При запуске из IDE укажите PostgreSQL через SPRING_DATASOURCE_URL,
SPRING_DATASOURCE_USERNAME и SPRING_DATASOURCE_PASSWORD. Если Auth работает
в Compose на порту 8080, задайте CATALOG_AUTH_BASE_URL=http://localhost:8080.

## Проверки

    .\gradlew.bat :auth:test :catalog:test

Для тестов JPA и полной интеграции Catalog нужен работающий Docker.
HTTP-тесты контроллеров и отдельные тесты HTTP-адаптера работают без БД.
CatalogLayerIntegrationTest запускает настоящий Catalog, PostgreSQL в
Testcontainers и контролируемый HTTP-сервис вместо Auth. Миграции на пустой
БД выполняет само приложение через Spring Boot Flyway starter. Тест проверяет
маппинг изображений после транзакции, чтение API из БД и поведение при 404/500 Auth.
SellerShopControllerTest проверяет настоящий SecurityFilterChain Auth:
публичный GET, 400/404, отсутствие личных полей и сохранение защиты других методов.

Корневой тест приложения в полном gradlew build требует доступную PostgreSQL
через те же SPRING_DATASOURCE_* переменные, как в Backend CI.

## Обновление US-2.2

Список товаров теперь возвращает объект `items`, `page`, `size`, `total` вместо массива.
Параметры поиска, фильтров, сортировки и ограничения страниц описаны в `catalog/src/catalog/web/README.md`.
Поиск выполняется через Elasticsearch, карточки читаются из PostgreSQL.
