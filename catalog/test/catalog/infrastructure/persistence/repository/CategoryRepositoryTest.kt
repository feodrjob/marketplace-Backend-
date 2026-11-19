package catalog.infrastructure.persistence.repository

import catalog.common.enums.ProductStatus
import catalog.infrastructure.persistence.entity.CategoryEntity
import catalog.infrastructure.persistence.entity.ProductEntity
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

class CategoryRepositoryTest : DatabaseUnitTestConfiguration() {

    @Autowired
    private lateinit var categoryRepository: CategoryRepository

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Nested
    @DisplayName("Позитивные сценарии (категории, иерархия, табы витрины и сортировка)")
    inner class PositiveScenarios {

        @Test
        @DisplayName("Создание и поиск корневой категории (таба витрины)")
        fun shouldCreateAndFindRootCategory() {
            val category = CategoryEntity(
                name = "Электроника",
                slug = "electronics-" + UUID.randomUUID(),
                sortOrder = 1,
                isActive = true
            )

            val saved = categoryRepository.save(category)
            assertNotNull(saved.id)
            assertNull(saved.parentId)
            assertEquals("Электроника", saved.name)
            assertTrue(saved.isActive)

            val found = categoryRepository.findById(saved.id!!)
            assertTrue(found.isPresent)
            assertEquals(saved.slug, found.get().slug)
            assertTrue(categoryRepository.existsBySlug(saved.slug))
        }

        @Test
        @DisplayName("Создание иерархии: родительская категория и дочерние подкатегории")
        @Transactional
        fun shouldSupportHierarchyWithSubcategories() {
            val root = categoryRepository.save(
                CategoryEntity(
                    name = "Бытовая техника",
                    slug = "appliances-" + UUID.randomUUID(),
                    sortOrder = 2,
                    isActive = true
                )
            )

            val sub1 = CategoryEntity(
                parentId = root.id,
                name = "Холодильники",
                slug = "refrigerators-" + UUID.randomUUID(),
                sortOrder = 1,
                isActive = true
            )

            val sub2 = CategoryEntity(
                parentId = root.id,
                name = "Стиральные машины",
                slug = "washing-machines-" + UUID.randomUUID(),
                sortOrder = 2,
                isActive = true
            )

            categoryRepository.save(sub1)
            categoryRepository.save(sub2)

            val subcategories = categoryRepository
                .findAllByParentIdAndIsActiveTrueOrderBySortOrderAscNameAsc(root.id!!)

            assertEquals(2, subcategories.size)
            assertEquals("Холодильники", subcategories[0].name)
            assertEquals("Стиральные машины", subcategories[1].name)
            assertTrue(categoryRepository.existsByParentId(root.id!!))
        }

        @Test
        @DisplayName("Фильтрация активных корневых категорий для табов витрины с сортировкой")
        fun shouldFindActiveRootCategoriesOrdered() {
            val suffix = UUID.randomUUID().toString().substring(0, 8)

            val catB = categoryRepository.save(
                CategoryEntity(
                    name = "Книги $suffix",
                    slug = "books-$suffix",
                    sortOrder = 20,
                    isActive = true
                )
            )

            val catA = categoryRepository.save(
                CategoryEntity(
                    name = "Автотовары $suffix",
                    slug = "auto-$suffix",
                    sortOrder = 10,
                    isActive = true
                )
            )

            val catInactive = categoryRepository.save(
                CategoryEntity(
                    name = "Архив $suffix",
                    slug = "archived-$suffix",
                    sortOrder = 5,
                    isActive = false
                )
            )

            val rootCategories = categoryRepository
                .findAllByParentIdIsNullAndIsActiveTrueOrderBySortOrderAscNameAsc()

            assertTrue(rootCategories.any { it.id == catA.id })
            assertTrue(rootCategories.any { it.id == catB.id })
            assertFalse(rootCategories.any { it.id == catInactive.id })

            // Проверяем, что в результирующем списке catA идет раньше catB по sortOrder
            var indexA = -1
            var indexB = -1
            for (i in rootCategories.indices) {
                if (rootCategories[i].id == catA.id) indexA = i
                if (rootCategories[i].id == catB.id) indexB = i
            }
            assertTrue(indexA < indexB, "catA (sortOrder 10) должен предшествовать catB (sortOrder 20)")
        }

        @Test
        @DisplayName("Каскадное удаление подкатегорий при удалении родительской категории")
        fun shouldCascadeDeleteSubcategoriesWhenParentDeleted() {
            val parent = categoryRepository.save(
                CategoryEntity(
                    name = "Спорт",
                    slug = "sports-" + UUID.randomUUID(),
                    sortOrder = 1,
                    isActive = true
                )
            )

            val child = categoryRepository.save(
                CategoryEntity(
                    parentId = parent.id,
                    name = "Велосипеды",
                    slug = "bicycles-" + UUID.randomUUID(),
                    sortOrder = 1,
                    isActive = true
                )
            )

            categoryRepository.deleteById(parent.id!!)

            assertFalse(categoryRepository.existsById(parent.id!!))
            assertFalse(categoryRepository.existsById(child.id!!))
        }

        @Test
        @DisplayName("Каскадное удаление трехуровневой иерархии (Дедушка -> Родитель -> Потомок)")
        fun shouldCascadeDeleteDeepHierarchyWhenGrandparentDeleted() {
            val grandparent = categoryRepository.save(
                CategoryEntity(
                    name = "Электроника глубокая",
                    slug = "deep-elec-" + UUID.randomUUID(),
                    sortOrder = 1,
                    isActive = true
                )
            )

            val parent = categoryRepository.save(
                CategoryEntity(
                    parentId = grandparent.id,
                    name = "Компьютеры",
                    slug = "computers-" + UUID.randomUUID(),
                    sortOrder = 1,
                    isActive = true
                )
            )

            val child = categoryRepository.save(
                CategoryEntity(
                    parentId = parent.id,
                    name = "Ноутбуки",
                    slug = "laptops-" + UUID.randomUUID(),
                    sortOrder = 1,
                    isActive = true
                )
            )

            categoryRepository.deleteById(grandparent.id!!)

            assertFalse(categoryRepository.existsById(grandparent.id!!))
            assertFalse(categoryRepository.existsById(parent.id!!))
            assertFalse(categoryRepository.existsById(child.id!!))
        }
    }

    @Nested
    @DisplayName("Негативные сценарии (ограничения целостности БД)")
    inner class NegativeScenarios {

        @Test
        @DisplayName("Ошибка при попытке создать категорию с неуникальным slug")
        fun shouldFailOnDuplicateSlug() {
            val duplicateSlug = "unique-slug-" + UUID.randomUUID()

            categoryRepository.saveAndFlush(
                CategoryEntity(
                    name = "Первая категория",
                    slug = duplicateSlug,
                    sortOrder = 1,
                    isActive = true
                )
            )

            val secondCategory = CategoryEntity(
                name = "Вторая категория",
                slug = duplicateSlug,
                sortOrder = 2,
                isActive = true
            )

            assertThrows(DataIntegrityViolationException::class.java) {
                categoryRepository.saveAndFlush(secondCategory)
            }
        }

        @Test
        @DisplayName("Ошибка при сохранении категории с пустым name")
        fun shouldFailOnBlankName() {
            val blankName = CategoryEntity(
                name = "   ",
                slug = "blank-name-" + UUID.randomUUID(),
                sortOrder = 1,
                isActive = true
            )

            assertThrows(DataIntegrityViolationException::class.java) {
                categoryRepository.saveAndFlush(blankName)
            }
        }

        @Test
        @DisplayName("Ошибка при сохранении категории с пустым slug")
        fun shouldFailOnBlankSlug() {
            val blankSlug = CategoryEntity(
                name = "Валидное имя",
                slug = "   ",
                sortOrder = 1,
                isActive = true
            )

            assertThrows(DataIntegrityViolationException::class.java) {
                categoryRepository.saveAndFlush(blankSlug)
            }
        }

        @Test
        @DisplayName("Ошибка внешнего ключа при указании несуществующего parent_id")
        fun shouldFailOnNonExistentParentId() {
            val orphan = CategoryEntity(
                parentId = UUID.randomUUID(),
                name = "Сирота",
                slug = "orphan-" + UUID.randomUUID(),
                sortOrder = 1,
                isActive = true
            )

            assertThrows(DataIntegrityViolationException::class.java) {
                categoryRepository.saveAndFlush(orphan)
            }
        }

        @Test
        @DisplayName("Ошибка внешнего ключа (RESTRICT) при попытке удалить категорию, к которой привязаны товары")
        fun shouldFailToDeleteCategoryWhenReferencedByProducts() {
            val category = categoryRepository.save(
                CategoryEntity(
                    name = "Категория с товаром",
                    slug = "cat-with-prod-" + UUID.randomUUID(),
                    sortOrder = 1,
                    isActive = true
                )
            )

            productRepository.save(
                ProductEntity(
                    sellerId = UUID.randomUUID(),
                    categoryId = category.id!!,
                    title = "Неотделимый товар",
                    description = "Товар привязан к категории",
                    price = 1000L,
                    status = ProductStatus.ACTIVE
                )
            )

            assertThrows(DataIntegrityViolationException::class.java) {
                categoryRepository.deleteById(category.id!!)
                categoryRepository.flush()
            }
        }
    }
}
