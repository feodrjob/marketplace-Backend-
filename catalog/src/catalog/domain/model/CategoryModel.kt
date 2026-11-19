package catalog.domain.model

import java.util.UUID

/**
 * Категория витрины.
 *
 * <p>
 * Дерево задаёт [parentId]: у корневого таба родителя нет.
 * Название и slug обрезаются и не могут быть пустыми.
 * </p>
 *
 * @param id идентификатор категории
 * @param parentId родитель или `null` для корневого таба
 * @param name отображаемое название, не длиннее [NAME_MAX_LENGTH]
 * @param slug URL-имя категории, не длиннее [SLUG_MAX_LENGTH]
 * @param sortOrder порядок таба, меньше — раньше
 * @param active `true`, если категория включена на витрине
 * @since 0.0.1
 * @author lllnightlll
 * @see CategoryNode
 */
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
