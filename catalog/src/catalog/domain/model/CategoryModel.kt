package catalog.domain.model

import java.util.UUID

class CategoryModel(
    val id: UUID,
    val parentId: UUID?,
    name: String?,
    slug: String?,
    val sortOrder: Int,
    val active: Boolean,
) {
    val name: String = requireBounded(name, "name", NAME_MAX_LENGTH)
    val slug: String = requireBounded(slug, "slug", SLUG_MAX_LENGTH)

    init {
        if (id == parentId) {
            throw IllegalArgumentException("Category cannot be its own parent")
        }
    }

    companion object {
        const val NAME_MAX_LENGTH = 100
        const val SLUG_MAX_LENGTH = 100

        private fun requireBounded(value: String?, field: String, maxLength: Int): String {
            val normalized = TextValues.trimToNull(value)
                ?: throw IllegalArgumentException("Field $field is required")
            if (normalized.length > maxLength) {
                throw IllegalArgumentException("Field $field is longer than $maxLength")
            }
            return normalized
        }
    }
}
