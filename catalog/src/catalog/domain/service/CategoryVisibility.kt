package catalog.domain.service

import catalog.domain.model.CategoryModel
import java.util.UUID

/**
 * Правило видимости категории на витрине.
 *
 * <p>
 * Категория публична, только если она сама и каждый предок активны.
 * Обрыв цепочки или цикл прячет ветку целиком, чтобы витрина не падала на битой ссылке.
 * </p>
 *
 * @since 0.0.1
 * @author lllnightlll
 * @see catalog.domain.model.CategoryModel
 */
class CategoryVisibility {
    fun visible(categories: Collection<CategoryModel>): List<CategoryModel> {
        val byId = index(categories)
        val cache = HashMap<UUID, Boolean>()
        return byId.values.filter { category -> isVisible(category, byId, cache) }
    }

    fun isVisible(category: CategoryModel?, byId: Map<UUID, CategoryModel>): Boolean {
        return isVisible(category, byId, HashMap())
    }

    fun index(categories: Collection<CategoryModel>): Map<UUID, CategoryModel> {
        val byId = LinkedHashMap<UUID, CategoryModel>()
        for (category in categories) {
            val previous = byId.put(category.id, category)
            if (previous != null) {
                throw IllegalArgumentException("Duplicate category id")
            }
        }
        return byId
    }

    private fun isVisible(
        category: CategoryModel?,
        byId: Map<UUID, CategoryModel>,
        cache: MutableMap<UUID, Boolean>,
    ): Boolean {
        return walk(category, byId, cache, HashSet())
    }

    private fun walk(
        category: CategoryModel?,
        byId: Map<UUID, CategoryModel>,
        cache: MutableMap<UUID, Boolean>,
        stack: MutableSet<UUID>,
    ): Boolean {
        if (category == null) {
            return false
        }
        cache[category.id]?.let { return it }
        if (!stack.add(category.id)) {
            return false
        }
        val visible = when {
            !category.active -> false
            category.parentId == null -> true
            else -> walk(byId[category.parentId], byId, cache, stack)
        }
        stack.remove(category.id)
        cache[category.id] = visible
        return visible
    }
}
