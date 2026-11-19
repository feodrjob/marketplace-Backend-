package catalog.domain.repository

import catalog.domain.model.ProductModel
import java.util.UUID

/**
 * Порт товаров.
 *
 * <p>
 * Возвращает товары всех статусов. Публичный список оставляет только
 * [catalog.common.enums.ProductStatus.ACTIVE] в активной ветке категорий.
 * </p>
 *
 * @since 0.0.1
 * @author lllnightlll
 * @see catalog.domain.model.ProductModel
 */
interface ProductRepository {
    fun findAll(): List<ProductModel>

    fun findById(id: UUID): ProductModel?
}
