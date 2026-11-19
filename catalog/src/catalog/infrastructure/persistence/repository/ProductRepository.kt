package catalog.infrastructure.persistence.repository

import catalog.common.enums.ProductStatus
import catalog.infrastructure.persistence.entity.ProductEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface ProductRepository : JpaRepository<ProductEntity, UUID>, JpaSpecificationExecutor<ProductEntity> {

    fun findByIdAndStatus(id: UUID, status: ProductStatus): Optional<ProductEntity>

    fun findAllByStatus(status: ProductStatus, pageable: Pageable): Page<ProductEntity>

    fun findAllByStatusAndCategoryId(status: ProductStatus, categoryId: UUID, pageable: Pageable): Page<ProductEntity>

    fun findAllByStatusAndCategoryIdIn(status: ProductStatus, categoryIds: Collection<UUID>, pageable: Pageable): Page<ProductEntity>

    fun findAllByStatusAndCategorySlug(status: ProductStatus, slug: String, pageable: Pageable): Page<ProductEntity>

    fun findAllByStatusAndCategorySlugIn(status: ProductStatus, slugs: Collection<String>, pageable: Pageable): Page<ProductEntity>

    fun findAllBySellerId(sellerId: UUID, pageable: Pageable): Page<ProductEntity>

    fun findAllBySellerIdAndStatus(sellerId: UUID, status: ProductStatus, pageable: Pageable): Page<ProductEntity>

    fun findAllBySellerIdAndCategoryId(sellerId: UUID, categoryId: UUID, pageable: Pageable): Page<ProductEntity>

    fun findAllBySellerIdAndCategorySlug(sellerId: UUID, slug: String, pageable: Pageable): Page<ProductEntity>

    fun existsByIdAndStatus(id: UUID, status: ProductStatus): Boolean

    fun existsBySellerId(sellerId: UUID): Boolean

    fun existsByCategoryId(categoryId: UUID): Boolean

    @Query(
        value = "SELECT * FROM catalog.products WHERE status = 'ACTIVE' AND search_vector @@ plainto_tsquery('russian', :query)",
        countQuery = "SELECT count(*) FROM catalog.products WHERE status = 'ACTIVE' AND search_vector @@ plainto_tsquery('russian', :query)",
        nativeQuery = true
    )
    fun searchActiveByText(@Param("query") query: String, pageable: Pageable): Page<ProductEntity>

    @Query(
        value = "SELECT * FROM catalog.products WHERE status = 'ACTIVE' AND (search_vector @@ plainto_tsquery('russian', :query) OR public.similarity(title, :query) > :similarityThreshold) ORDER BY ts_rank(search_vector, plainto_tsquery('russian', :query)) DESC, public.similarity(title, :query) DESC",
        countQuery = "SELECT count(*) FROM catalog.products WHERE status = 'ACTIVE' AND (search_vector @@ plainto_tsquery('russian', :query) OR public.similarity(title, :query) > :similarityThreshold)",
        nativeQuery = true
    )
    fun searchActiveByTextAndTrigram(
        @Param("query") query: String,
        @Param("similarityThreshold") similarityThreshold: Double,
        pageable: Pageable
    ): Page<ProductEntity>
}
