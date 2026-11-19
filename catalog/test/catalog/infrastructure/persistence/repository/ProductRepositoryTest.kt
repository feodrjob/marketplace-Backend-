package catalog.infrastructure.persistence.repository

import catalog.common.enums.ProductStatus
import catalog.infrastructure.persistence.entity.CategoryEntity
import catalog.infrastructure.persistence.entity.ProductEntity
import catalog.infrastructure.persistence.entity.ProductImageEntity
import catalog.infrastructure.persistence.entity.ProductImageId
import jakarta.persistence.EntityManager
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

class ProductRepositoryTest : DatabaseUnitTestConfiguration() {

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var productImageRepository: ProductImageRepository

    @Autowired
    private lateinit var categoryRepository: CategoryRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var defaultCategory: CategoryEntity

    @BeforeEach
    fun setUp() {
        defaultCategory = categoryRepository.save(
            CategoryEntity(
                name = "Общая категория",
                slug = "general-" + UUID.randomUUID(),
                sortOrder = 0,
                isActive = true
            )
        )
    }

    private fun createCategory(name: String, slugPrefix: String): CategoryEntity {
        return categoryRepository.save(
            CategoryEntity(
                name = name,
                slug = "$slugPrefix-" + UUID.randomUUID(),
                sortOrder = 1,
                isActive = true
            )
        )
    }

    @Nested
    @DisplayName("Позитивные сценарии (CRUD, фильтрация, связи и полнотекстовый поиск)")
    inner class PositiveScenarios {

        @Test
        @DisplayName("Сохранение и поиск активного товара без изображений")
        fun shouldSaveAndFindProductWithoutImages() {
            val sellerId = UUID.randomUUID()
            val category = createCategory("Электроника", "electronics")

            val product = ProductEntity(
                sellerId = sellerId,
                categoryId = category.id!!,
                title = "Ноутбук Pro 16",
                description = "Мощный рабочий ноутбук с отличным экраном",
                price = 150000L,
                status = ProductStatus.ACTIVE
            )

            val saved = productRepository.save(product)
            assertNotNull(saved.id)
            assertNotNull(saved.createdAt)
            assertNotNull(saved.updatedAt)

            val found = productRepository.findById(saved.id!!)
            assertTrue(found.isPresent)
            assertEquals("Ноутбук Pro 16", found.get().title)
            assertEquals(category.id, found.get().categoryId)
            assertEquals(150000L, found.get().price)
            assertEquals(ProductStatus.ACTIVE, found.get().status)
            assertEquals(sellerId, found.get().sellerId)

            assertTrue(productRepository.existsByIdAndStatus(saved.id!!, ProductStatus.ACTIVE))
            assertFalse(productRepository.existsByIdAndStatus(saved.id!!, ProductStatus.HIDDEN))
            assertTrue(productRepository.existsBySellerId(sellerId))
            assertTrue(productRepository.existsByCategoryId(category.id!!))
        }

        @Test
        @DisplayName("Сохранение товара с изображениями и соблюдение порядка sort_order")
        @Transactional
        fun shouldSaveProductWithImagesAndMaintainSortOrder() {
            val sellerId = UUID.randomUUID()
            val mediaId1 = UUID.randomUUID()
            val mediaId2 = UUID.randomUUID()
            val category = createCategory("Одежда", "clothing")

            val product = ProductEntity(
                sellerId = sellerId,
                categoryId = category.id!!,
                title = "Кожаная куртка",
                description = "Стильная осенняя куртка",
                price = 12000L,
                status = ProductStatus.ACTIVE
            )

            product.addImage(mediaId2, 10)
            product.addImage(mediaId1, 1)

            val saved = productRepository.saveAndFlush(product)
            assertNotNull(saved.id)

            entityManager.clear()

            val found = productRepository.findById(saved.id!!).orElseThrow()
            assertEquals(2, found.images.size)
            assertEquals(mediaId1, found.images[0].id?.mediaId)
            assertEquals(1, found.images[0].sortOrder)
            assertEquals(mediaId2, found.images[1].id?.mediaId)
            assertEquals(10, found.images[1].sortOrder)

            assertTrue(productImageRepository.existsByIdMediaId(mediaId1))
            assertEquals(1L, productImageRepository.countByIdMediaId(mediaId1))
        }

        @Test
        @DisplayName("Выборка активных товаров с пагинацией и сортировкой по цене")
        fun shouldFindActiveProductsWithPaginationAndSorting() {
            val sellerId = UUID.randomUUID()
            val category = createCategory("Гаджеты", "gadgets")

            val cheapActive = ProductEntity(
                sellerId = sellerId,
                categoryId = category.id!!,
                title = "Чехол для телефона",
                price = 500L,
                status = ProductStatus.ACTIVE
            )

            val expensiveActive = ProductEntity(
                sellerId = sellerId,
                categoryId = category.id!!,
                title = "Смартфон Ultra",
                price = 80000L,
                status = ProductStatus.ACTIVE
            )

            val hiddenProduct = ProductEntity(
                sellerId = sellerId,
                categoryId = category.id!!,
                title = "Скрытый кабель",
                price = 100L,
                status = ProductStatus.HIDDEN
            )

            val archivedProduct = ProductEntity(
                sellerId = sellerId,
                categoryId = category.id!!,
                title = "Архивный адаптер",
                price = 200L,
                status = ProductStatus.ARCHIVED
            )

            productRepository.saveAll(listOf(cheapActive, expensiveActive, hiddenProduct, archivedProduct))

            val page = productRepository.findAllByStatus(
                ProductStatus.ACTIVE,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "price"))
            )

            val items = page.content
            assertTrue(items.size >= 2)
            assertTrue(items.all { it.status == ProductStatus.ACTIVE })

            for (i in 0 until items.size - 1) {
                assertTrue(items[i].price <= items[i + 1].price)
            }
        }

        @Test
        @DisplayName("Фильтрация активных товаров по категории и нескольким категориям")
        fun shouldFilterActiveProductsByCategory() {
            val sellerId = UUID.randomUUID()
            val booksCat = createCategory("Книги", "books")
            val gadgetsCat = createCategory("Гаджеты", "gadgets")

            val book = ProductEntity(
                sellerId = sellerId,
                categoryId = booksCat.id!!,
                title = "Чистая архитектура",
                price = 2500L,
                status = ProductStatus.ACTIVE
            )

            val gadget = ProductEntity(
                sellerId = sellerId,
                categoryId = gadgetsCat.id!!,
                title = "Умные часы",
                price = 15000L,
                status = ProductStatus.ACTIVE
            )

            productRepository.saveAll(listOf(book, gadget))

            val booksPage = productRepository.findAllByStatusAndCategoryId(
                ProductStatus.ACTIVE,
                booksCat.id!!,
                PageRequest.of(0, 10)
            )
            assertTrue(booksPage.content.any { it.title == "Чистая архитектура" })
            assertTrue(booksPage.content.none { it.title == "Умные часы" })

            val multipleCategoriesPage = productRepository.findAllByStatusAndCategoryIdIn(
                ProductStatus.ACTIVE,
                listOf(booksCat.id!!, gadgetsCat.id!!),
                PageRequest.of(0, 10)
            )
            assertTrue(multipleCategoriesPage.content.any { it.title == "Чистая архитектура" })
            assertTrue(multipleCategoriesPage.content.any { it.title == "Умные часы" })

            val slugPage = productRepository.findAllByStatusAndCategorySlug(
                ProductStatus.ACTIVE,
                booksCat.slug,
                PageRequest.of(0, 10)
            )
            assertTrue(slugPage.content.any { it.title == "Чистая архитектура" })
            assertTrue(slugPage.content.none { it.title == "Умные часы" })

            val multipleSlugsPage = productRepository.findAllByStatusAndCategorySlugIn(
                ProductStatus.ACTIVE,
                listOf(booksCat.slug, gadgetsCat.slug),
                PageRequest.of(0, 10)
            )
            assertTrue(multipleSlugsPage.content.any { it.title == "Чистая архитектура" })
            assertTrue(multipleSlugsPage.content.any { it.title == "Умные часы" })
        }

        @Test
        @DisplayName("Поиск товаров продавца с фильтрацией по статусу и категории")
        fun shouldFindProductsBySellerIdAndFilterByStatus() {
            val sellerId = UUID.randomUUID()
            val cat = createCategory("Товары продавца", "seller-cat")

            val active = ProductEntity(
                sellerId = sellerId,
                categoryId = cat.id!!,
                title = "Товар 1 продавца",
                price = 1000L,
                status = ProductStatus.ACTIVE
            )

            val hidden = ProductEntity(
                sellerId = sellerId,
                categoryId = cat.id!!,
                title = "Товар 2 продавца (скрытый)",
                price = 2000L,
                status = ProductStatus.HIDDEN
            )

            productRepository.saveAll(listOf(active, hidden))

            val allSellerProducts = productRepository.findAllBySellerId(sellerId, PageRequest.of(0, 10))
            assertEquals(2L, allSellerProducts.totalElements)

            val hiddenOnly = productRepository.findAllBySellerIdAndStatus(sellerId, ProductStatus.HIDDEN, PageRequest.of(0, 10))
            assertEquals(1L, hiddenOnly.totalElements)
            assertEquals("Товар 2 продавца (скрытый)", hiddenOnly.content[0].title)

            val sellerCategoryPage = productRepository.findAllBySellerIdAndCategoryId(sellerId, cat.id!!, PageRequest.of(0, 10))
            assertEquals(2L, sellerCategoryPage.totalElements)
        }

        @Test
        @DisplayName("Обновление карточки товара и перевод в другой статус")
        fun shouldUpdateProductDetailsAndStatus() {
            val product = ProductEntity(
                sellerId = UUID.randomUUID(),
                categoryId = defaultCategory.id!!,
                title = "Оригинальный заголовок",
                price = 1000L,
                status = ProductStatus.ACTIVE
            )

            val saved = productRepository.save(product)

            saved.title = "Обновленный заголовок"
            saved.price = 1200L
            saved.status = ProductStatus.HIDDEN

            val updated = productRepository.save(saved)
            assertEquals("Обновленный заголовок", updated.title)
            assertEquals(1200L, updated.price)
            assertEquals(ProductStatus.HIDDEN, updated.status)
        }

        @Test
        @DisplayName("Каскадное удаление изображений при удалении товара")
        fun shouldCascadeDeleteProductImagesWhenProductDeleted() {
            val mediaId = UUID.randomUUID()
            val product = ProductEntity(
                sellerId = UUID.randomUUID(),
                categoryId = defaultCategory.id!!,
                title = "Товар для удаления",
                price = 999L,
                status = ProductStatus.ACTIVE
            )

            product.addImage(mediaId, 1)
            val saved = productRepository.saveAndFlush(product)

            assertTrue(productImageRepository.existsByIdMediaId(mediaId))

            productRepository.delete(saved)
            productRepository.flush()

            assertFalse(productRepository.existsById(saved.id!!))
            assertFalse(productImageRepository.existsByIdMediaId(mediaId))
        }

        @Test
        @DisplayName("Orphan removal при удалении изображения из коллекции товара")
        @Transactional
        fun shouldSupportOrphanRemovalWhenImagesListModified() {
            val mediaId1 = UUID.randomUUID()
            val mediaId2 = UUID.randomUUID()

            val product = ProductEntity(
                sellerId = UUID.randomUUID(),
                categoryId = defaultCategory.id!!,
                title = "Товар с двумя фото",
                price = 500L,
                status = ProductStatus.ACTIVE
            )

            product.addImage(mediaId1, 1)
            product.addImage(mediaId2, 2)
            val saved = productRepository.saveAndFlush(product)

            assertEquals(2, saved.images.size)

            saved.removeImage(mediaId1)
            productRepository.saveAndFlush(saved)

            val refreshed = productRepository.findById(saved.id!!).orElseThrow()
            assertEquals(1, refreshed.images.size)
            assertEquals(mediaId2, refreshed.images[0].id?.mediaId)
            assertFalse(productImageRepository.existsByIdMediaId(mediaId1))
            assertTrue(productImageRepository.existsByIdMediaId(mediaId2))
        }

        @Test
        @DisplayName("Полнотекстовый поиск по русской морфологии через search_vector")
        fun shouldPerformFullTextSearchInRussian() {
            val audioCat = createCategory("Аудиотехника", "audio")
            val appCat = createCategory("Бытовая техника", "appliances")

            val p1 = ProductEntity(
                sellerId = UUID.randomUUID(),
                categoryId = audioCat.id!!,
                title = "Флагманские беспроводные наушники",
                description = "Превосходное шумоподавление и кристальный звук",
                price = 25000L,
                status = ProductStatus.ACTIVE
            )

            val p2 = ProductEntity(
                sellerId = UUID.randomUUID(),
                categoryId = appCat.id!!,
                title = "Электрический чайник из стали",
                description = "Быстрое кипячение воды с подсветкой",
                price = 3500L,
                status = ProductStatus.ACTIVE
            )

            productRepository.saveAllAndFlush(listOf(p1, p2))

            val result = productRepository.searchActiveByText("наушники", PageRequest.of(0, 10))
            assertTrue(result.content.any { it.title.contains("наушники") })
            assertTrue(result.content.none { it.title.contains("чайник") })

            val descResult = productRepository.searchActiveByText("шумоподавление", PageRequest.of(0, 10))
            assertTrue(descResult.content.any { it.title.contains("наушники") })

            val kettleResult = productRepository.searchActiveByText("кипячение", PageRequest.of(0, 10))
            assertTrue(kettleResult.content.any { it.title.contains("чайник") })
        }

        @Test
        @DisplayName("Нечеткий поиск (триграммы) с устойчивостью к опечаткам")
        fun shouldSupportTrigramSearchOrTypo() {
            val tvCat = createCategory("Видео", "video")

            val product = ProductEntity(
                sellerId = UUID.randomUUID(),
                categoryId = tvCat.id!!,
                title = "Телевизор диагональ 65 дюймов",
                description = "Разрешение 4K HDR",
                price = 75000L,
                status = ProductStatus.ACTIVE
            )

            productRepository.saveAndFlush(product)

            val result = productRepository.searchActiveByTextAndTrigram(
                "телевизар",
                0.2,
                PageRequest.of(0, 10)
            )
            assertTrue(result.content.any { it.title.contains("Телевизор") })
        }

        @Test
        @DisplayName("Корнер кейс: FTS триггер корректно индексирует товар с null-описанием")
        fun shouldHandleNullDescriptionInFtsTriggerAndFindProduct() {
            val category = createCategory("Гаджеты", "gadgets")
            val product = ProductEntity(
                sellerId = UUID.randomUUID(),
                categoryId = category.id!!,
                title = "Умный браслет Фитнес",
                description = null,
                price = 3000L,
                status = ProductStatus.ACTIVE
            )

            productRepository.saveAndFlush(product)

            val searchResult = productRepository.searchActiveByText("браслет", PageRequest.of(0, 10))
            assertTrue(searchResult.content.any { it.title == "Умный браслет Фитнес" })
        }

        @Test
        @DisplayName("Корнер кейс: FTS триггер пересчитывает search_vector при обновлении title")
        fun shouldUpdateSearchVectorWhenTitleOrDescriptionUpdated() {
            val category = createCategory("Мобильные устройства", "mobile")
            val product = ProductEntity(
                sellerId = UUID.randomUUID(),
                categoryId = category.id!!,
                title = "Смартфон начального уровня",
                description = "Бюджетный аппарат",
                price = 10000L,
                status = ProductStatus.ACTIVE
            )

            val saved = productRepository.saveAndFlush(product)
            assertTrue(productRepository.searchActiveByText("смартфон", PageRequest.of(0, 10)).content.any { it.id == saved.id })

            saved.title = "Планшет начального уровня"
            productRepository.saveAndFlush(saved)

            // После обновления title search_vector должен найти "планшет" и не находить "смартфон"
            val tabletResult = productRepository.searchActiveByText("планшет", PageRequest.of(0, 10))
            assertTrue(tabletResult.content.any { it.id == saved.id })

            val phoneResult = productRepository.searchActiveByText("смартфон", PageRequest.of(0, 10))
            assertFalse(phoneResult.content.any { it.id == saved.id })
        }

        @Test
        @DisplayName("Корнер кейс: поиск устойчив к регистру и поддерживает русскую морфологию (склонения)")
        fun shouldMatchRussianMorphologyAndCaseInsensitive() {
            val category = createCategory("Клининг", "cleaning")
            val product = ProductEntity(
                sellerId = UUID.randomUUID(),
                categoryId = category.id!!,
                title = "Моющий беспроводной пылесос",
                description = "Уборка шерсти домашних животных без усилий",
                price = 18000L,
                status = ProductStatus.ACTIVE
            )

            productRepository.saveAndFlush(product)

            // Поиск в верхнем регистре
            val upperCaseResult = productRepository.searchActiveByText("ПЫЛЕСОС", PageRequest.of(0, 10))
            assertTrue(upperCaseResult.content.any { it.title.contains("пылесос") })

            // Склонение "пылесосом" (творительный падеж)
            val inflectedResult = productRepository.searchActiveByText("пылесосом", PageRequest.of(0, 10))
            assertTrue(inflectedResult.content.any { it.title.contains("пылесос") })

            // Множественное число в описании "животное" (в описании "животных")
            val descMorphResult = productRepository.searchActiveByText("животное", PageRequest.of(0, 10))
            assertTrue(descMorphResult.content.any { it.title.contains("пылесос") })
        }

        @Test
        @DisplayName("Инвариант витрины: товар ACTIVE не виден в поиске и витрине, если его категория неактивна")
        fun shouldHideProductWhenCategoryIsInactive() {
            val inactiveCategory = categoryRepository.save(
                CategoryEntity(
                    name = "Сезонная категория",
                    slug = "seasonal-" + UUID.randomUUID(),
                    sortOrder = 99,
                    isActive = false
                )
            )

            val product = ProductEntity(
                sellerId = UUID.randomUUID(),
                categoryId = inactiveCategory.id!!,
                title = "Снегоуборщик бензиновый",
                description = "Мощная зимняя техника",
                price = 45000L,
                status = ProductStatus.ACTIVE
            )

            val saved = productRepository.saveAndFlush(product)

            // В БД товар существует и ACTIVE
            assertTrue(productRepository.existsByIdAndStatus(saved.id!!, ProductStatus.ACTIVE))

            // Но для витрины с проверкой category.is_active = true он недоступен
            assertFalse(productRepository.existsByIdAndStatusAndCategoryIsActiveTrue(saved.id!!, ProductStatus.ACTIVE))
            assertTrue(productRepository.findByIdAndStatusAndCategoryIsActiveTrue(saved.id!!, ProductStatus.ACTIVE).isEmpty)

            val searchResult = productRepository.searchActiveByText("снегоуборщик", PageRequest.of(0, 10))
            assertFalse(searchResult.content.any { it.id == saved.id })

            val storefrontPage = productRepository.findAllByStatusAndCategoryIsActiveTrue(ProductStatus.ACTIVE, PageRequest.of(0, 10))
            assertFalse(storefrontPage.content.any { it.id == saved.id })
        }

        @Test
        @DisplayName("US-3.1 AC 4: удаление одного товара не удаляет общий медиафайл, используемый другим товаром")
        fun shouldPreserveSharedMediaWhenOneProductDeleted() {
            val sharedMediaId = UUID.randomUUID()
            val category = createCategory("Общая категория медиа", "shared-media")

            val p1 = ProductEntity(
                sellerId = UUID.randomUUID(),
                categoryId = category.id!!,
                title = "Товар 1 с общим фото",
                price = 1000L,
                status = ProductStatus.ACTIVE
            )
            p1.addImage(sharedMediaId, 1)
            val savedP1 = productRepository.saveAndFlush(p1)

            val p2 = ProductEntity(
                sellerId = UUID.randomUUID(),
                categoryId = category.id!!,
                title = "Товар 2 с общим фото",
                price = 2000L,
                status = ProductStatus.ACTIVE
            )
            p2.addImage(sharedMediaId, 1)
            val savedP2 = productRepository.saveAndFlush(p2)

            assertEquals(2L, productImageRepository.countByIdMediaId(sharedMediaId))
            assertTrue(productImageRepository.existsByIdMediaId(sharedMediaId))

            // Удаляем первый товар
            productRepository.delete(savedP1)
            productRepository.flush()

            // Проверяем, что первый товар удален, но второй остался и ссылка на медиа жива
            assertFalse(productRepository.existsById(savedP1.id!!))
            assertTrue(productRepository.existsById(savedP2.id!!))
            assertEquals(1L, productImageRepository.countByIdMediaId(sharedMediaId))
            assertTrue(productImageRepository.existsByIdMediaId(sharedMediaId))
        }

        @Test
        @DisplayName("Orphan removal: метод clearImages() удаляет все связанные изображения")
        @Transactional
        fun shouldClearAllImagesViaClearImagesMethod() {
            val mediaId1 = UUID.randomUUID()
            val mediaId2 = UUID.randomUUID()
            val category = createCategory("Очистка фото", "clear-images")

            val product = ProductEntity(
                sellerId = UUID.randomUUID(),
                categoryId = category.id!!,
                title = "Товар для полной очистки фото",
                price = 5000L,
                status = ProductStatus.ACTIVE
            )
            product.addImage(mediaId1, 1)
            product.addImage(mediaId2, 2)
            val saved = productRepository.saveAndFlush(product)

            assertEquals(2, saved.images.size)
            assertTrue(productImageRepository.existsByIdMediaId(mediaId1))
            assertTrue(productImageRepository.existsByIdMediaId(mediaId2))

            saved.clearImages()
            productRepository.saveAndFlush(saved)

            val refreshed = productRepository.findById(saved.id!!).orElseThrow()
            assertTrue(refreshed.images.isEmpty())
            assertFalse(productImageRepository.existsByIdMediaId(mediaId1))
            assertFalse(productImageRepository.existsByIdMediaId(mediaId2))
        }

        @Test
        @DisplayName("Межсервисный API 4.3 (Commerce checkout): пакетный поиск findAllByIdIn и findAllByIdInAndStatus")
        fun shouldSupportBatchQueriesForCheckoutValidation() {
            val category = createCategory("Пакетная валидация", "batch-val")

            val pActive1 = productRepository.save(
                ProductEntity(
                    sellerId = UUID.randomUUID(),
                    categoryId = category.id!!,
                    title = "Активный товар 1",
                    price = 100L,
                    status = ProductStatus.ACTIVE
                )
            )

            val pActive2 = productRepository.save(
                ProductEntity(
                    sellerId = UUID.randomUUID(),
                    categoryId = category.id!!,
                    title = "Активный товар 2",
                    price = 200L,
                    status = ProductStatus.ACTIVE
                )
            )

            val pHidden = productRepository.save(
                ProductEntity(
                    sellerId = UUID.randomUUID(),
                    categoryId = category.id!!,
                    title = "Скрытый товар",
                    price = 300L,
                    status = ProductStatus.HIDDEN
                )
            )

            val nonExistentId = UUID.randomUUID()
            val queryIds = listOf(pActive1.id!!, pActive2.id!!, pHidden.id!!, nonExistentId)

            val allFound = productRepository.findAllByIdIn(queryIds)
            assertEquals(3, allFound.size)
            assertTrue(allFound.any { it.id == pActive1.id })
            assertTrue(allFound.any { it.id == pActive2.id })
            assertTrue(allFound.any { it.id == pHidden.id })

            val activeFound = productRepository.findAllByIdInAndStatus(queryIds, ProductStatus.ACTIVE)
            assertEquals(2, activeFound.size)
            assertTrue(activeFound.any { it.id == pActive1.id })
            assertTrue(activeFound.any { it.id == pActive2.id })
            assertFalse(activeFound.any { it.id == pHidden.id })
        }
    }

    @Nested
    @DisplayName("Негативные сценарии (проверка CHECK, FK и NOT NULL констрейнтов)")
    inner class NegativeScenarios {

        @Test
        @DisplayName("Ошибка внешнего ключа при указании несуществующей категории category_id")
        fun shouldFailWhenCategoryIdDoesNotExist() {
            val invalidCategory = ProductEntity(
                sellerId = UUID.randomUUID(),
                categoryId = UUID.randomUUID(),
                title = "Товар с несуществующей категорией",
                price = 1000L,
                status = ProductStatus.ACTIVE
            )

            assertThrows(DataIntegrityViolationException::class.java) {
                productRepository.saveAndFlush(invalidCategory)
            }
        }

        @Test
        @DisplayName("Защита целостности: запрет удаления категории, если к ней привязаны товары (ON DELETE RESTRICT)")
        fun shouldPreventCategoryDeletionWhenProductsExist() {
            val protectedCategory = createCategory("Защищенная категория", "protected-cat")

            val product = ProductEntity(
                sellerId = UUID.randomUUID(),
                categoryId = protectedCategory.id!!,
                title = "Привязанный товар",
                price = 1000L,
                status = ProductStatus.ACTIVE
            )

            productRepository.saveAndFlush(product)

            assertThrows(DataIntegrityViolationException::class.java) {
                categoryRepository.deleteById(protectedCategory.id!!)
                categoryRepository.flush()
            }
        }

        @Test
        @DisplayName("Ошибка при неположительной цене (price <= 0)")
        fun shouldFailWhenPriceIsNotPositive() {
            val zeroPrice = ProductEntity(
                sellerId = UUID.randomUUID(),
                categoryId = defaultCategory.id!!,
                title = "Бесплатный товар",
                price = 0L,
                status = ProductStatus.ACTIVE
            )

            assertThrows(DataIntegrityViolationException::class.java) {
                productRepository.saveAndFlush(zeroPrice)
            }

            val negativePrice = ProductEntity(
                sellerId = UUID.randomUUID(),
                categoryId = defaultCategory.id!!,
                title = "Отрицательная цена",
                price = -100L,
                status = ProductStatus.ACTIVE
            )

            assertThrows(DataIntegrityViolationException::class.java) {
                productRepository.saveAndFlush(negativePrice)
            }
        }

        @Test
        @DisplayName("Ошибка при пустом или состоящем из пробелов названии")
        fun shouldFailWhenTitleIsBlank() {
            val emptyTitle = ProductEntity(
                sellerId = UUID.randomUUID(),
                categoryId = defaultCategory.id!!,
                title = "",
                price = 100L,
                status = ProductStatus.ACTIVE
            )

            assertThrows(DataIntegrityViolationException::class.java) {
                productRepository.saveAndFlush(emptyTitle)
            }

            val spacesTitle = ProductEntity(
                sellerId = UUID.randomUUID(),
                categoryId = defaultCategory.id!!,
                title = "   ",
                price = 100L,
                status = ProductStatus.ACTIVE
            )

            assertThrows(DataIntegrityViolationException::class.java) {
                productRepository.saveAndFlush(spacesTitle)
            }
        }

        @Test
        @Transactional
        @DisplayName("Ошибка при дубликате составного первичного ключа (product_id, media_id)")
        fun shouldFailWhenDuplicateProductImageKey() {
            val mediaId = UUID.randomUUID()
            val product = ProductEntity(
                sellerId = UUID.randomUUID(),
                categoryId = defaultCategory.id!!,
                title = "Товар с дубликатом медиа",
                price = 100L,
                status = ProductStatus.ACTIVE
            )

            product.addImage(mediaId, 1)
            val saved = productRepository.saveAndFlush(product)

            entityManager.clear()

            val duplicateImage = ProductImageEntity(
                id = ProductImageId(saved.id, mediaId),
                product = saved,
                sortOrder = 2
            )

            assertThrows(ConstraintViolationException::class.java) {
                entityManager.persist(duplicateImage)
                entityManager.flush()
            }
        }
    }
}
