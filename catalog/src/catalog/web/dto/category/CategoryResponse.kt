package catalog.web.dto.category

import java.util.UUID

/*
* Описывает категорию и вложенные категории в HTTP ответе
* */
data class CategoryResponse(
    val id: UUID,
    val parentId: UUID?,
    val name: String,
    val slug: String,
    val sortOrder: Int,
    val children: List<CategoryResponse>,
)
