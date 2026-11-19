package catalog.infrastructure.persistence.repository

import catalog.infrastructure.persistence.entity.ProductImageEntity
import catalog.infrastructure.persistence.entity.ProductImageId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ProductImageRepository : JpaRepository<ProductImageEntity, ProductImageId> {

    fun findByIdProductIdOrderBySortOrderAsc(productId: UUID): List<ProductImageEntity>

    fun existsByIdMediaId(mediaId: UUID): Boolean

    fun countByIdMediaId(mediaId: UUID): Long
}
