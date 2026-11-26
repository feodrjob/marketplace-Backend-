# Infrastructure слой модуля catalog
Реализация слоя персистентности:
- `persistence/entity`: сущности базы данных (`ProductEntity`, `ProductImageEntity`).
- `persistence/repository`: Spring Data JPA репозитории (`ProductRepository`, `ProductImageRepository`).
- `auth`: контракт публичного магазина, получаемого по HTTP из Auth.
