package catalog.infrastructure.persistence.adapter

import catalog.domain.model.ProductImageRef
import catalog.domain.model.ProductModel
import catalog.domain.repository.ProductRepository
import catalog.infrastructure.persistence.entity.ProductEntity
import catalog.infrastructure.persistence.repository.ProductRepository as JpaProductRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/*
* Связывает доменный репозиторий товаров и изображений с PostgreSQL
* */
@Repository
@Transactional(readOnly = true)
class ProductRepositoryAdapter(
    private val entities: JpaProductRepository,
) : ProductRepository {
    override fun findAll(): List<ProductModel> = entities.findAll().map { toModel(it) }

    override fun findById(id: UUID): ProductModel? = entities.findById(id).orElse(null)?.let { toModel(it) }

    override fun findAllByIds(ids: Collection<UUID>): List<ProductModel> =
        if (ids.isEmpty()) emptyList() else entities.findAllByIdIn(ids).map { toModel(it) }

    private fun toModel(entity: ProductEntity): ProductModel = ProductModel(
        id = checkNotNull(entity.id),
        sellerId = entity.sellerId,
        categoryId = entity.categoryId,
        title = entity.title,
        description = entity.description,
        price = entity.price,
        status = entity.status,
        images = entity.images.map { image ->
            ProductImageRef(
                productId = checkNotNull(image.id?.productId),
                mediaId = checkNotNull(image.id?.mediaId),
                sortOrder = image.sortOrder,
            )
        },
        createdAt = checkNotNull(entity.createdAt),
        updatedAt = checkNotNull(entity.updatedAt),
    )
}
