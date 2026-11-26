package catalog.web.mapper

import catalog.domain.model.CategoryNode
import catalog.web.dto.category.CategoryResponse
import org.springframework.stereotype.Component

/*
* Преобразует доменное дерево категорий в HTTP ответ
* */
@Component
class CategoryDtoMapper {
    fun toResponse(category: CategoryNode): CategoryResponse = CategoryResponse(
        id = category.id,
        parentId = category.parentId,
        name = category.name,
        slug = category.slug,
        sortOrder = category.sortOrder,
        children = category.children.map { toResponse(it) },
    )
}
