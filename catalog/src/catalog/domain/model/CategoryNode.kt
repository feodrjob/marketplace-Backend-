package catalog.domain.model

import java.util.UUID

class CategoryNode(
    val id: UUID,
    val parentId: UUID?,
    val name: String,
    val slug: String,
    val sortOrder: Int,
    children: List<CategoryNode>,
) {
    val children: List<CategoryNode> = children.toList()
}
