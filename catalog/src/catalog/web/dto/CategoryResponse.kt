package catalog.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

/**
 * Категория в публичном ответе API.
 */
@Schema(description = "Узел дерева или плоского списка публичных категорий", requiredProperties = ["id", "parentId", "name", "slug", "sortOrder", "children"])
data class CategoryResponse(
    @field:Schema(description = "Идентификатор категории", example = "00000000-0000-0000-0000-000000000001")
    val id: UUID,
    @field:Schema(description = "Идентификатор родителя или null для корневой категории", example = "00000000-0000-0000-0000-000000000002", types = ["string", "null"], format = "uuid")
    val parentId: UUID?,
    @field:Schema(description = "Название категории", example = "Книги")
    val name: String,
    @field:Schema(description = "Slug категории для фильтрации товаров", example = "books")
    val slug: String,
    @field:Schema(description = "Порядок категории среди соседей", example = "0")
    val sortOrder: Int,
    @field:Schema(description = "Видимые дочерние категории, пустой массив при tree=false или отсутствии потомков")
    val children: List<CategoryResponse>,
)