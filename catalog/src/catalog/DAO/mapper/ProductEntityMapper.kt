package catalog.DAO.mapper

import catalog.domain.model.ProductImageRef
import catalog.domain.model.ProductModel
import catalog.infrastructure.persistence.entity.ProductEntity
import catalog.infrastructure.persistence.entity.ProductImageEntity
import org.springframework.stereotype.Component

/**
 * Преобразует JPA-товары в доменные модели.
 */
@Component
class ProductEntityMapper {

    // Преобразует товар в доменную модель.
    fun toDomain(entity: ProductEntity): ProductModel {
        return ProductModel(
            id = checkNotNull(entity.id) {
                "У сохранённого товара отсутствует идентификатор"
            },
            sellerId = entity.sellerId,
            categoryId = entity.categoryId,
            title = entity.title,
            description = entity.description,
            price = entity.price,
            status = entity.status,
            images = entity.images.map { toImageRef(it) },
            createdAt = checkNotNull(entity.createdAt) {
                "У сохранённого товара отсутствует дата создания"
            },
            updatedAt = checkNotNull(entity.updatedAt) {
                "У сохранённого товара отсутствует дата обновления"
            },
        )
    }

    // Преобразует связь с изображением.
    private fun toImageRef(entity: ProductImageEntity): ProductImageRef {
        val imageId = checkNotNull(entity.id) {
            "У изображения отсутствует составной идентификатор"
        }

        return ProductImageRef(
            productId = checkNotNull(imageId.productId) {
                "У изображения отсутствует идентификатор товара"
            },
            mediaId = checkNotNull(imageId.mediaId) {
                "У изображения отсутствует идентификатор файла"
            },
            sortOrder = entity.sortOrder,
        )
    }
}