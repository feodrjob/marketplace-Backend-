package catalog.domain.repository

import catalog.domain.model.CategoryModel
import java.util.UUID

/**
 * Порт категорий.
 *
 * <p>
 * Возвращает все сохранённые строки, включая неактивные.
 * Какие из них видны гостю, решает [catalog.domain.service.PublicCatalogService].
 * </p>
 *
 * @since 0.0.1
 * @author lllnightlll
 * @see catalog.domain.model.CategoryModel
 */
interface CategoryRepository {
    fun findAll(): List<CategoryModel>

    fun findById(id: UUID): CategoryModel?
}
