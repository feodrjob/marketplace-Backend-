package catalog.domain.model

import java.util.UUID

/**
 * Запрос публичного списка категорий.
 *
 * <p>
 * [TREE_BY_DEFAULT] включает дерево. [parentId] ограничивает ответ
 * поддеревом этого родителя, сам родитель в ответ не входит.
 * </p>
 *
 * @param tree `true` — вложенное дерево, `false` — плоский список
 * @param parentId категория, чьи потомки нужны, или `null` для всей витрины
 * @since 0.0.1
 * @author lllnightlll
 * @see CategoryNode
 */
class CategoryListingQuery(
    val tree: Boolean,
    val parentId: UUID?,
) {
    companion object {
        const val TREE_BY_DEFAULT = true

        fun storefront(): CategoryListingQuery = CategoryListingQuery(TREE_BY_DEFAULT, null)
    }
}
