package catalog.domain.model

import java.util.UUID

class CategoryListingQuery(
    val tree: Boolean,
    val parentId: UUID?,
) {
    companion object {
        const val TREE_BY_DEFAULT = true

        fun storefront(): CategoryListingQuery = CategoryListingQuery(TREE_BY_DEFAULT, null)
    }
}
