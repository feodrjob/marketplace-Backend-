package catalog.web.mapper

import catalog.domain.model.CategoryNode
import catalog.web.dto.CategoryResponse
import org.springframework.stereotype.Component

/**
 * Преобразует доменные категории в DTO.
 */
@Component
class CategoryDtoMapper {

    // Преобразует категорию в ответ.
    fun toResponse(category: CategoryNode): CategoryResponse {
        return CategoryResponse(
            id = category.id,
            parentId = category.parentId,
            name = category.name,
            slug = category.slug,
            sortOrder = category.sortOrder,
            children = toResponseList(category.children),
        )
    }

    // Преобразует список категорий.
    fun toResponseList(categories: List<CategoryNode>): List<CategoryResponse> {
        return categories.map { toResponse(it) }
    }
}