package catalog.domain.support

import catalog.domain.model.ProductModel
import catalog.domain.repository.ProductRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryProductRepository : ProductRepository {
    private val products = ConcurrentHashMap<UUID, ProductModel>()

    fun save(product: ProductModel) {
        products[product.id] = product
    }

    override fun findAll(): List<ProductModel> = products.values.toList()

    override fun findById(id: UUID): ProductModel? = products[id]
}
