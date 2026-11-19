package catalog.domain.model

import java.util.UUID

/**
 * Узел публичного дерева категорий.
 *
 * <p>
 * В плоском списке [children] пустой. В дереве здесь лежат только
 * видимые потомки, уже отсортированные.
 * </p>
 *
 * @param id идентификатор категории
 * @param parentId родитель или `null` для корня витрины
 * @param name отображаемое название
 * @param slug URL-имя категории
 * @param sortOrder порядок среди соседей
 * @param children вложенные публичные категории
 * @since 0.0.1
 * @author lllnightlll
 * @see CategoryModel
 */
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
