package catalog.infrastructure.persistence.repository;

import catalog.common.enums.ProductStatus;
import catalog.infrastructure.persistence.entity.CategoryEntity;
import catalog.infrastructure.persistence.entity.ProductEntity;
import catalog.infrastructure.persistence.entity.ProductImageEntity;
import catalog.infrastructure.persistence.entity.ProductImageId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProductRepositoryTest extends DatabaseUnitTestConfiguration {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private CategoryEntity defaultCategory;

    @BeforeEach
    void setUp() {
        defaultCategory = categoryRepository.save(CategoryEntity.builder()
                .name("Общая категория")
                .slug("general-" + UUID.randomUUID())
                .sortOrder(0)
                .isActive(true)
                .build());
    }

    private CategoryEntity createCategory(String name, String slugPrefix) {
        return categoryRepository.save(CategoryEntity.builder()
                .name(name)
                .slug(slugPrefix + "-" + UUID.randomUUID())
                .sortOrder(1)
                .isActive(true)
                .build());
    }

    @Nested
    @DisplayName("Позитивные сценарии (CRUD, фильтрация, связи и полнотекстовый поиск)")
    class PositiveScenarios {

        @Test
        @DisplayName("Сохранение и поиск активного товара без изображений")
        void shouldSaveAndFindProductWithoutImages() {
            UUID sellerId = UUID.randomUUID();
            CategoryEntity category = createCategory("Электроника", "electronics");

            ProductEntity product = ProductEntity.builder()
                    .sellerId(sellerId)
                    .categoryId(category.getId())
                    .title("Ноутбук Pro 16")
                    .description("Мощный рабочий ноутбук с отличным экраном")
                    .price(150000L)
                    .status(ProductStatus.ACTIVE)
                    .build();

            ProductEntity saved = productRepository.save(product);
            assertNotNull(saved.getId());
            assertNotNull(saved.getCreatedAt());
            assertNotNull(saved.getUpdatedAt());

            Optional<ProductEntity> found = productRepository.findById(saved.getId());
            assertTrue(found.isPresent());
            assertEquals("Ноутбук Pro 16", found.get().getTitle());
            assertEquals(category.getId(), found.get().getCategoryId());
            assertEquals(150000L, found.get().getPrice());
            assertEquals(ProductStatus.ACTIVE, found.get().getStatus());
            assertEquals(sellerId, found.get().getSellerId());

            assertTrue(productRepository.existsByIdAndStatus(saved.getId(), ProductStatus.ACTIVE));
            assertFalse(productRepository.existsByIdAndStatus(saved.getId(), ProductStatus.HIDDEN));
            assertTrue(productRepository.existsBySellerId(sellerId));
            assertTrue(productRepository.existsByCategoryId(category.getId()));
        }

        @Test
        @DisplayName("Сохранение товара с изображениями и соблюдение порядка sort_order")
        @Transactional
        void shouldSaveProductWithImagesAndMaintainSortOrder() {
            UUID sellerId = UUID.randomUUID();
            UUID mediaId1 = UUID.randomUUID();
            UUID mediaId2 = UUID.randomUUID();
            CategoryEntity category = createCategory("Одежда", "clothing");

            ProductEntity product = ProductEntity.builder()
                    .sellerId(sellerId)
                    .categoryId(category.getId())
                    .title("Кожаная куртка")
                    .description("Стильная осенняя куртка")
                    .price(12000L)
                    .status(ProductStatus.ACTIVE)
                    .build();

            product.addImage(mediaId2, 10);
            product.addImage(mediaId1, 1);

            ProductEntity saved = productRepository.saveAndFlush(product);
            assertNotNull(saved.getId());

            entityManager.clear();

            ProductEntity found = productRepository.findById(saved.getId()).orElseThrow();
            assertEquals(2, found.getImages().size());
            assertEquals(mediaId1, found.getImages().get(0).getId().getMediaId());
            assertEquals(1, found.getImages().get(0).getSortOrder());
            assertEquals(mediaId2, found.getImages().get(1).getId().getMediaId());
            assertEquals(10, found.getImages().get(1).getSortOrder());

            assertTrue(productImageRepository.existsByIdMediaId(mediaId1));
            assertEquals(1, productImageRepository.countByIdMediaId(mediaId1));
        }

        @Test
        @DisplayName("Выборка активных товаров с пагинацией и сортировкой по цене")
        void shouldFindActiveProductsWithPaginationAndSorting() {
            UUID sellerId = UUID.randomUUID();
            CategoryEntity category = createCategory("Гаджеты", "gadgets");

            ProductEntity cheapActive = ProductEntity.builder()
                    .sellerId(sellerId)
                    .categoryId(category.getId())
                    .title("Чехол для телефона")
                    .price(500L)
                    .status(ProductStatus.ACTIVE)
                    .build();

            ProductEntity expensiveActive = ProductEntity.builder()
                    .sellerId(sellerId)
                    .categoryId(category.getId())
                    .title("Смартфон Ultra")
                    .price(80000L)
                    .status(ProductStatus.ACTIVE)
                    .build();

            ProductEntity hiddenProduct = ProductEntity.builder()
                    .sellerId(sellerId)
                    .categoryId(category.getId())
                    .title("Скрытый кабель")
                    .price(100L)
                    .status(ProductStatus.HIDDEN)
                    .build();

            ProductEntity archivedProduct = ProductEntity.builder()
                    .sellerId(sellerId)
                    .categoryId(category.getId())
                    .title("Архивный адаптер")
                    .price(200L)
                    .status(ProductStatus.ARCHIVED)
                    .build();

            productRepository.saveAll(List.of(cheapActive, expensiveActive, hiddenProduct, archivedProduct));

            Page<ProductEntity> page = productRepository.findAllByStatus(
                    ProductStatus.ACTIVE,
                    PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "price"))
            );

            List<ProductEntity> items = page.getContent();
            assertTrue(items.size() >= 2);
            assertTrue(items.stream().allMatch(p -> p.getStatus() == ProductStatus.ACTIVE));

            // Проверяем сортировку по возрастанию цены
            for (int i = 0; i < items.size() - 1; i++) {
                assertTrue(items.get(i).getPrice() <= items.get(i + 1).getPrice());
            }
        }

        @Test
        @DisplayName("Фильтрация активных товаров по категории и нескольким категориям")
        void shouldFilterActiveProductsByCategory() {
            UUID sellerId = UUID.randomUUID();
            CategoryEntity booksCat = createCategory("Книги", "books");
            CategoryEntity gadgetsCat = createCategory("Гаджеты", "gadgets");

            ProductEntity book = ProductEntity.builder()
                    .sellerId(sellerId)
                    .categoryId(booksCat.getId())
                    .title("Чистая архитектура")
                    .price(2500L)
                    .status(ProductStatus.ACTIVE)
                    .build();

            ProductEntity gadget = ProductEntity.builder()
                    .sellerId(sellerId)
                    .categoryId(gadgetsCat.getId())
                    .title("Умные часы")
                    .price(15000L)
                    .status(ProductStatus.ACTIVE)
                    .build();

            productRepository.saveAll(List.of(book, gadget));

            Page<ProductEntity> booksPage = productRepository.findAllByStatusAndCategoryId(
                    ProductStatus.ACTIVE,
                    booksCat.getId(),
                    PageRequest.of(0, 10)
            );
            assertTrue(booksPage.getContent().stream().anyMatch(p -> p.getTitle().equals("Чистая архитектура")));
            assertTrue(booksPage.getContent().stream().noneMatch(p -> p.getTitle().equals("Умные часы")));

            Page<ProductEntity> multipleCategoriesPage = productRepository.findAllByStatusAndCategoryIdIn(
                    ProductStatus.ACTIVE,
                    List.of(booksCat.getId(), gadgetsCat.getId()),
                    PageRequest.of(0, 10)
            );
            assertTrue(multipleCategoriesPage.getContent().stream().anyMatch(p -> p.getTitle().equals("Чистая архитектура")));
            assertTrue(multipleCategoriesPage.getContent().stream().anyMatch(p -> p.getTitle().equals("Умные часы")));
        }

        @Test
        @DisplayName("Поиск товаров продавца с фильтрацией по статусу и категории")
        void shouldFindProductsBySellerIdAndFilterByStatus() {
            UUID sellerId = UUID.randomUUID();
            CategoryEntity cat = createCategory("Товары продавца", "seller-cat");

            ProductEntity active = ProductEntity.builder()
                    .sellerId(sellerId)
                    .categoryId(cat.getId())
                    .title("Товар 1 продавца")
                    .price(1000L)
                    .status(ProductStatus.ACTIVE)
                    .build();

            ProductEntity hidden = ProductEntity.builder()
                    .sellerId(sellerId)
                    .categoryId(cat.getId())
                    .title("Товар 2 продавца (скрытый)")
                    .price(2000L)
                    .status(ProductStatus.HIDDEN)
                    .build();

            productRepository.saveAll(List.of(active, hidden));

            Page<ProductEntity> allSellerProducts = productRepository.findAllBySellerId(sellerId, PageRequest.of(0, 10));
            assertEquals(2, allSellerProducts.getTotalElements());

            Page<ProductEntity> hiddenOnly = productRepository.findAllBySellerIdAndStatus(sellerId, ProductStatus.HIDDEN, PageRequest.of(0, 10));
            assertEquals(1, hiddenOnly.getTotalElements());
            assertEquals("Товар 2 продавца (скрытый)", hiddenOnly.getContent().get(0).getTitle());

            Page<ProductEntity> sellerCategoryPage = productRepository.findAllBySellerIdAndCategoryId(sellerId, cat.getId(), PageRequest.of(0, 10));
            assertEquals(2, sellerCategoryPage.getTotalElements());
        }

        @Test
        @DisplayName("Обновление карточки товара и перевод в другой статус")
        void shouldUpdateProductDetailsAndStatus() {
            ProductEntity product = ProductEntity.builder()
                    .sellerId(UUID.randomUUID())
                    .categoryId(defaultCategory.getId())
                    .title("Оригинальный заголовок")
                    .price(1000L)
                    .status(ProductStatus.ACTIVE)
                    .build();

            ProductEntity saved = productRepository.save(product);

            saved.setTitle("Обновленный заголовок");
            saved.setPrice(1200L);
            saved.setStatus(ProductStatus.HIDDEN);

            ProductEntity updated = productRepository.save(saved);
            assertEquals("Обновленный заголовок", updated.getTitle());
            assertEquals(1200L, updated.getPrice());
            assertEquals(ProductStatus.HIDDEN, updated.getStatus());
        }

        @Test
        @DisplayName("Каскадное удаление изображений при удалении товара")
        void shouldCascadeDeleteProductImagesWhenProductDeleted() {
            UUID mediaId = UUID.randomUUID();
            ProductEntity product = ProductEntity.builder()
                    .sellerId(UUID.randomUUID())
                    .categoryId(defaultCategory.getId())
                    .title("Товар для удаления")
                    .price(999L)
                    .status(ProductStatus.ACTIVE)
                    .build();

            product.addImage(mediaId, 1);
            ProductEntity saved = productRepository.saveAndFlush(product);

            assertTrue(productImageRepository.existsByIdMediaId(mediaId));

            productRepository.delete(saved);
            productRepository.flush();

            assertFalse(productRepository.existsById(saved.getId()));
            assertFalse(productImageRepository.existsByIdMediaId(mediaId));
        }

        @Test
        @DisplayName("Orphan removal при удалении изображения из коллекции товара")
        @Transactional
        void shouldSupportOrphanRemovalWhenImagesListModified() {
            UUID mediaId1 = UUID.randomUUID();
            UUID mediaId2 = UUID.randomUUID();

            ProductEntity product = ProductEntity.builder()
                    .sellerId(UUID.randomUUID())
                    .categoryId(defaultCategory.getId())
                    .title("Товар с двумя фото")
                    .price(500L)
                    .status(ProductStatus.ACTIVE)
                    .build();

            product.addImage(mediaId1, 1);
            product.addImage(mediaId2, 2);
            ProductEntity saved = productRepository.saveAndFlush(product);

            assertEquals(2, saved.getImages().size());

            saved.removeImage(mediaId1);
            productRepository.saveAndFlush(saved);

            ProductEntity refreshed = productRepository.findById(saved.getId()).orElseThrow();
            assertEquals(1, refreshed.getImages().size());
            assertEquals(mediaId2, refreshed.getImages().get(0).getId().getMediaId());
            assertFalse(productImageRepository.existsByIdMediaId(mediaId1));
            assertTrue(productImageRepository.existsByIdMediaId(mediaId2));
        }

        @Test
        @DisplayName("Полнотекстовый поиск по русской морфологии через search_vector")
        void shouldPerformFullTextSearchInRussian() {
            CategoryEntity audioCat = createCategory("Аудиотехника", "audio");
            CategoryEntity appCat = createCategory("Бытовая техника", "appliances");

            ProductEntity p1 = ProductEntity.builder()
                    .sellerId(UUID.randomUUID())
                    .categoryId(audioCat.getId())
                    .title("Флагманские беспроводные наушники")
                    .description("Превосходное шумоподавление и кристальный звук")
                    .price(25000L)
                    .status(ProductStatus.ACTIVE)
                    .build();

            ProductEntity p2 = ProductEntity.builder()
                    .sellerId(UUID.randomUUID())
                    .categoryId(appCat.getId())
                    .title("Электрический чайник из стали")
                    .description("Быстрое кипячение воды с подсветкой")
                    .price(3500L)
                    .status(ProductStatus.ACTIVE)
                    .build();

            productRepository.saveAllAndFlush(List.of(p1, p2));

            // Поиск по корню/словоформе "наушник"
            Page<ProductEntity> result = productRepository.searchActiveByText("наушники", PageRequest.of(0, 10));
            assertTrue(result.getContent().stream().anyMatch(p -> p.getTitle().contains("наушники")));
            assertTrue(result.getContent().stream().noneMatch(p -> p.getTitle().contains("чайник")));

            // Поиск по слову из описания "шумоподавление"
            Page<ProductEntity> descResult = productRepository.searchActiveByText("шумоподавление", PageRequest.of(0, 10));
            assertTrue(descResult.getContent().stream().anyMatch(p -> p.getTitle().contains("наушники")));

            // Поиск чайника по слову "кипячение"
            Page<ProductEntity> kettleResult = productRepository.searchActiveByText("кипячение", PageRequest.of(0, 10));
            assertTrue(kettleResult.getContent().stream().anyMatch(p -> p.getTitle().contains("чайник")));
        }

        @Test
        @DisplayName("Нечеткий поиск (триграммы) с устойчивостью к опечаткам")
        void shouldSupportTrigramSearchOrTypo() {
            CategoryEntity tvCat = createCategory("Видео", "video");

            ProductEntity product = ProductEntity.builder()
                    .sellerId(UUID.randomUUID())
                    .categoryId(tvCat.getId())
                    .title("Телевизор диагональ 65 дюймов")
                    .description("Разрешение 4K HDR")
                    .price(75000L)
                    .status(ProductStatus.ACTIVE)
                    .build();

            productRepository.saveAndFlush(product);

            // Поиск с легкой опечаткой "телевизар"
            Page<ProductEntity> result = productRepository.searchActiveByTextAndTrigram(
                    "телевизар",
                    0.2,
                    PageRequest.of(0, 10)
            );
            assertTrue(result.getContent().stream().anyMatch(p -> p.getTitle().contains("Телевизор")));
        }
    }

    @Nested
    @DisplayName("Негативные сценарии (проверка CHECK, FK и NOT NULL констрейнтов)")
    class NegativeScenarios {

        @Test
        @DisplayName("Ошибка внешнего ключа при указании несуществующей категории category_id")
        void shouldFailWhenCategoryIdDoesNotExist() {
            ProductEntity invalidCategory = ProductEntity.builder()
                    .sellerId(UUID.randomUUID())
                    .categoryId(UUID.randomUUID())
                    .title("Товар с несуществующей категорией")
                    .price(1000L)
                    .status(ProductStatus.ACTIVE)
                    .build();

            assertThrows(DataIntegrityViolationException.class, () -> productRepository.saveAndFlush(invalidCategory));
        }

        @Test
        @DisplayName("Защита целостности: запрет удаления категории, если к ней привязаны товары (ON DELETE RESTRICT)")
        void shouldPreventCategoryDeletionWhenProductsExist() {
            CategoryEntity protectedCategory = createCategory("Защищенная категория", "protected-cat");

            ProductEntity product = ProductEntity.builder()
                    .sellerId(UUID.randomUUID())
                    .categoryId(protectedCategory.getId())
                    .title("Привязанный товар")
                    .price(1000L)
                    .status(ProductStatus.ACTIVE)
                    .build();

            productRepository.saveAndFlush(product);

            assertThrows(DataIntegrityViolationException.class, () -> {
                categoryRepository.deleteById(protectedCategory.getId());
                categoryRepository.flush();
            });
        }

        @Test
        @DisplayName("Ошибка при отсутствии category_id (not null constraint)")
        void shouldFailWhenCategoryIdIsNull() {
            ProductEntity noCategory = ProductEntity.builder()
                    .sellerId(UUID.randomUUID())
                    .categoryId(null)
                    .title("Товар без категории")
                    .price(100L)
                    .status(ProductStatus.ACTIVE)
                    .build();

            assertThrows(DataIntegrityViolationException.class, () -> productRepository.saveAndFlush(noCategory));
        }

        @Test
        @DisplayName("Ошибка при неположительной цене (price <= 0)")
        void shouldFailWhenPriceIsNotPositive() {
            ProductEntity zeroPrice = ProductEntity.builder()
                    .sellerId(UUID.randomUUID())
                    .categoryId(defaultCategory.getId())
                    .title("Бесплатный товар")
                    .price(0L)
                    .status(ProductStatus.ACTIVE)
                    .build();

            assertThrows(DataIntegrityViolationException.class, () -> productRepository.saveAndFlush(zeroPrice));

            ProductEntity negativePrice = ProductEntity.builder()
                    .sellerId(UUID.randomUUID())
                    .categoryId(defaultCategory.getId())
                    .title("Отрицательная цена")
                    .price(-100L)
                    .status(ProductStatus.ACTIVE)
                    .build();

            assertThrows(DataIntegrityViolationException.class, () -> productRepository.saveAndFlush(negativePrice));
        }

        @Test
        @DisplayName("Ошибка при пустом или состоящем из пробелов названии")
        void shouldFailWhenTitleIsBlank() {
            ProductEntity emptyTitle = ProductEntity.builder()
                    .sellerId(UUID.randomUUID())
                    .categoryId(defaultCategory.getId())
                    .title("")
                    .price(100L)
                    .status(ProductStatus.ACTIVE)
                    .build();

            assertThrows(DataIntegrityViolationException.class, () -> productRepository.saveAndFlush(emptyTitle));

            ProductEntity spacesTitle = ProductEntity.builder()
                    .sellerId(UUID.randomUUID())
                    .categoryId(defaultCategory.getId())
                    .title("   ")
                    .price(100L)
                    .status(ProductStatus.ACTIVE)
                    .build();

            assertThrows(DataIntegrityViolationException.class, () -> productRepository.saveAndFlush(spacesTitle));
        }

        @Test
        @DisplayName("Ошибка при отсутствии seller_id (not null constraint)")
        void shouldFailWhenSellerIdIsNull() {
            ProductEntity noSeller = ProductEntity.builder()
                    .sellerId(null)
                    .categoryId(defaultCategory.getId())
                    .title("Товар без продавца")
                    .price(100L)
                    .status(ProductStatus.ACTIVE)
                    .build();

            assertThrows(DataIntegrityViolationException.class, () -> productRepository.saveAndFlush(noSeller));
        }

        @Test
        @Transactional
        @DisplayName("Ошибка при дубликате составного первичного ключа (product_id, media_id)")
        void shouldFailWhenDuplicateProductImageKey() {
            UUID mediaId = UUID.randomUUID();
            ProductEntity product = ProductEntity.builder()
                    .sellerId(UUID.randomUUID())
                    .categoryId(defaultCategory.getId())
                    .title("Товар с дубликатом медиа")
                    .price(100L)
                    .status(ProductStatus.ACTIVE)
                    .build();

            product.addImage(mediaId, 1);
            ProductEntity saved = productRepository.saveAndFlush(product);

            entityManager.clear();

            ProductImageEntity duplicateImage = ProductImageEntity.builder()
                    .id(new ProductImageId(saved.getId(), mediaId))
                    .product(saved)
                    .sortOrder(2)
                    .build();

            assertThrows(org.hibernate.exception.ConstraintViolationException.class, () -> {
                entityManager.persist(duplicateImage);
                entityManager.flush();
            });
        }
    }
}
