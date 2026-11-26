package catalog.DAO.repositoryImpl

import catalog.DAO.mapper.ProductEntityMapper
import catalog.domain.model.ProductModel
import catalog.domain.repository.ProductRepository
import catalog.infrastructure.persistence.repository.ProductRepository as JpaProductRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Подключает доменное хранилище товаров к JPA.
 */
@Repository
@Transactional(readOnly = true)
class ProductModelRepositoryImpl(
    private val entities: JpaProductRepository,
    private val mapper: ProductEntityMapper,
) : ProductRepository {

    // Загружает все товары из БД.
    override fun findAll(): List<ProductModel> {
        return entities.findAll().map { mapper.toDomain(it) }
    }

    // Загружает товар по идентификатору.
    override fun findById(id: UUID): ProductModel? {
        return entities.findById(id)
            .map { mapper.toDomain(it) }
            .orElse(null)
    }
}