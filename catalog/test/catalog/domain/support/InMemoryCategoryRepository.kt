package catalog.domain.support

import catalog.domain.model.CategoryModel
import catalog.domain.repository.CategoryRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryCategoryRepository : CategoryRepository {
    private val categories = ConcurrentHashMap<UUID, CategoryModel>()

    fun save(category: CategoryModel) {
        categories[category.id] = category
    }

    override fun findAll(): List<CategoryModel> = categories.values.toList()

    override fun findById(id: UUID): CategoryModel? = categories[id]
}
