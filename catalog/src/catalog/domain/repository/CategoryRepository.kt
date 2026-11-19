package catalog.domain.repository

import catalog.domain.model.CategoryModel
import java.util.UUID

interface CategoryRepository {
    fun findAll(): List<CategoryModel>

    fun findById(id: UUID): CategoryModel?
}
