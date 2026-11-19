package catalog.DAO.mapper

import catalog.domain.model.CategoryModel
import catalog.infrastructure.persistence.entity.CategoryEntity
import org.springframework.stereotype.Component

/**
 * Преобразует сущности категорий в модели.
 */
@Component
class CategoryEntityMapper {

    // Преобразует сущность в доменную модель.
    fun toDomain(entity: CategoryEntity): CategoryModel {
        return CategoryModel(
            id = checkNotNull(entity.id) {
                "У сохранённой категории отсутствует идентификатор"
            },
            parentId = entity.parentId,
            name = entity.name,
            slug = entity.slug,
            sortOrder = entity.sortOrder,
            active = entity.isActive,
        )
    }
}