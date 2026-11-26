package catalog.web.dto

import java.util.UUID

/**
 * Категория в публичном ответе API.
 */
data class CategoryResponse(
    val id: UUID,
    val parentId: UUID?,
    val name: String,
    val slug: String,
    val sortOrder: Int,
    val children: List<CategoryResponse>,
)