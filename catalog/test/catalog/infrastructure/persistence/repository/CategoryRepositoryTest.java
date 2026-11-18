package catalog.infrastructure.persistence.repository;

import catalog.infrastructure.persistence.entity.CategoryEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CategoryRepositoryTest extends DatabaseUnitTestConfiguration {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Nested
    @DisplayName("Позитивные сценарии (категории, иерархия, табы витрины и сортировка)")
    class PositiveScenarios {

        @Test
        @DisplayName("Создание и поиск корневой категории (таба витрины)")
        void shouldCreateAndFindRootCategory() {
            CategoryEntity category = CategoryEntity.builder()
                    .name("Электроника")
                    .slug("electronics-" + UUID.randomUUID())
                    .sortOrder(1)
                    .isActive(true)
                    .build();

            CategoryEntity saved = categoryRepository.save(category);
            assertNotNull(saved.getId());
            assertNull(saved.getParentId());
            assertEquals("Электроника", saved.getName());
            assertTrue(saved.getIsActive());

            Optional<CategoryEntity> found = categoryRepository.findById(saved.getId());
            assertTrue(found.isPresent());
            assertEquals(saved.getSlug(), found.get().getSlug());
            assertTrue(categoryRepository.existsBySlug(saved.getSlug()));
        }

        @Test
        @DisplayName("Создание иерархии: родительская категория и дочерние подкатегории")
        @Transactional
        void shouldSupportHierarchyWithSubcategories() {
            CategoryEntity root = categoryRepository.save(CategoryEntity.builder()
                    .name("Бытовая техника")
                    .slug("appliances-" + UUID.randomUUID())
                    .sortOrder(2)
                    .isActive(true)
                    .build());

            CategoryEntity sub1 = CategoryEntity.builder()
                    .parentId(root.getId())
                    .name("Холодильники")
                    .slug("refrigerators-" + UUID.randomUUID())
                    .sortOrder(1)
                    .isActive(true)
                    .build();

            CategoryEntity sub2 = CategoryEntity.builder()
                    .parentId(root.getId())
                    .name("Стиральные машины")
                    .slug("washing-machines-" + UUID.randomUUID())
                    .sortOrder(2)
                    .isActive(true)
                    .build();

            categoryRepository.save(sub1);
            categoryRepository.save(sub2);

            List<CategoryEntity> subcategories = categoryRepository
                    .findAllByParentIdAndIsActiveTrueOrderBySortOrderAscNameAsc(root.getId());

            assertEquals(2, subcategories.size());
            assertEquals("Холодильники", subcategories.get(0).getName());
            assertEquals("Стиральные машины", subcategories.get(1).getName());
            assertTrue(categoryRepository.existsByParentId(root.getId()));
        }

        @Test
        @DisplayName("Фильтрация активных корневых категорий для табов витрины с сортировкой")
        void shouldFindActiveRootCategoriesOrdered() {
            String suffix = UUID.randomUUID().toString().substring(0, 8);

            CategoryEntity catB = categoryRepository.save(CategoryEntity.builder()
                    .name("Книги " + suffix)
                    .slug("books-" + suffix)
                    .sortOrder(20)
                    .isActive(true)
                    .build());

            CategoryEntity catA = categoryRepository.save(CategoryEntity.builder()
                    .name("Автотовары " + suffix)
                    .slug("auto-" + suffix)
                    .sortOrder(10)
                    .isActive(true)
                    .build());

            CategoryEntity catInactive = categoryRepository.save(CategoryEntity.builder()
                    .name("Архив " + suffix)
                    .slug("archived-" + suffix)
                    .sortOrder(5)
                    .isActive(false)
                    .build());

            List<CategoryEntity> rootCategories = categoryRepository
                    .findAllByParentIdIsNullAndIsActiveTrueOrderBySortOrderAscNameAsc();

            assertTrue(rootCategories.stream().anyMatch(c -> c.getId().equals(catA.getId())));
            assertTrue(rootCategories.stream().anyMatch(c -> c.getId().equals(catB.getId())));
            assertFalse(rootCategories.stream().anyMatch(c -> c.getId().equals(catInactive.getId())));

            // Проверяем, что в результирующем списке catA идет раньше catB по sortOrder
            int indexA = -1;
            int indexB = -1;
            for (int i = 0; i < rootCategories.size(); i++) {
                if (rootCategories.get(i).getId().equals(catA.getId())) indexA = i;
                if (rootCategories.get(i).getId().equals(catB.getId())) indexB = i;
            }
            assertTrue(indexA < indexB, "catA (sortOrder 10) должен предшествовать catB (sortOrder 20)");
        }

        @Test
        @DisplayName("Каскадное удаление подкатегорий при удалении родительской категории")
        void shouldCascadeDeleteSubcategoriesWhenParentDeleted() {
            CategoryEntity parent = categoryRepository.save(CategoryEntity.builder()
                    .name("Спорт")
                    .slug("sports-" + UUID.randomUUID())
                    .sortOrder(1)
                    .isActive(true)
                    .build());

            CategoryEntity child = categoryRepository.save(CategoryEntity.builder()
                    .parentId(parent.getId())
                    .name("Велосипеды")
                    .slug("bicycles-" + UUID.randomUUID())
                    .sortOrder(1)
                    .isActive(true)
                    .build());

            categoryRepository.deleteById(parent.getId());

            assertFalse(categoryRepository.existsById(parent.getId()));
            assertFalse(categoryRepository.existsById(child.getId()));
        }
    }

    @Nested
    @DisplayName("Негативные сценарии (ограничения целостности БД)")
    class NegativeScenarios {

        @Test
        @DisplayName("Ошибка при попытке создать категорию с неуникальным slug")
        void shouldFailOnDuplicateSlug() {
            String duplicateSlug = "unique-slug-" + UUID.randomUUID();

            categoryRepository.saveAndFlush(CategoryEntity.builder()
                    .name("Первая категория")
                    .slug(duplicateSlug)
                    .sortOrder(1)
                    .isActive(true)
                    .build());

            CategoryEntity secondCategory = CategoryEntity.builder()
                    .name("Вторая категория")
                    .slug(duplicateSlug)
                    .sortOrder(2)
                    .isActive(true)
                    .build();

            assertThrows(DataIntegrityViolationException.class, () -> {
                categoryRepository.saveAndFlush(secondCategory);
            });
        }

        @Test
        @DisplayName("Ошибка при сохранении категории с пустым name")
        void shouldFailOnBlankName() {
            CategoryEntity blankName = CategoryEntity.builder()
                    .name("   ")
                    .slug("blank-name-" + UUID.randomUUID())
                    .sortOrder(1)
                    .isActive(true)
                    .build();

            assertThrows(DataIntegrityViolationException.class, () -> {
                categoryRepository.saveAndFlush(blankName);
            });
        }

        @Test
        @DisplayName("Ошибка при сохранении категории с пустым slug")
        void shouldFailOnBlankSlug() {
            CategoryEntity blankSlug = CategoryEntity.builder()
                    .name("Валидное имя")
                    .slug("   ")
                    .sortOrder(1)
                    .isActive(true)
                    .build();

            assertThrows(DataIntegrityViolationException.class, () -> {
                categoryRepository.saveAndFlush(blankSlug);
            });
        }

        @Test
        @DisplayName("Ошибка внешнего ключа при указании несуществующего parent_id")
        void shouldFailOnNonExistentParentId() {
            CategoryEntity orphan = CategoryEntity.builder()
                    .parentId(UUID.randomUUID())
                    .name("Сирота")
                    .slug("orphan-" + UUID.randomUUID())
                    .sortOrder(1)
                    .isActive(true)
                    .build();

            assertThrows(DataIntegrityViolationException.class, () -> {
                categoryRepository.saveAndFlush(orphan);
            });
        }
    }
}
