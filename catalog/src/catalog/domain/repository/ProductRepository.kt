package catalog.domain.repository

import catalog.domain.model.ProductModel
import java.util.UUID

interface ProductRepository {
    fun findAll(): List<ProductModel>

    fun findById(id: UUID): ProductModel?
}
