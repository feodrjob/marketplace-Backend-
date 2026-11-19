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

    // 1. Поиск по ID и статусу (для детальной карточки товара на витрине)
    fun findByIdAndStatus(id: UUID, status: ProductStatus): Optional<ProductEntity>

    // 2. Пакетная выборка для чекаута в Commerce Service (API 4.3)
    fun findAllByIdIn(ids: Collection<UUID>): List<ProductEntity>

    fun findAllByIdInAndStatus(ids: Collection<UUID>, status: ProductStatus): List<ProductEntity>

    // 3. Проверки ссылочной целостности и ограничений
    fun existsByIdAndStatus(id: UUID, status: ProductStatus): Boolean

    fun existsBySellerId(sellerId: UUID): Boolean

    fun existsByCategoryId(categoryId: UUID): Boolean

    // 4. Нативный полнотекстовый поиск PostgreSQL (tsvector, русская морфология)
    @Query(
        value = "SELECT p.* FROM catalog.products p JOIN catalog.categories c ON p.category_id = c.id WHERE p.status = 'ACTIVE' AND c.is_active = true AND p.search_vector @@ plainto_tsquery('russian', :query)",
        countQuery = "SELECT count(p.id) FROM catalog.products p JOIN catalog.categories c ON p.category_id = c.id WHERE p.status = 'ACTIVE' AND c.is_active = true AND p.search_vector @@ plainto_tsquery('russian', :query)",
        nativeQuery = true
    )
    fun searchActiveByText(@Param("query") query: String, pageable: Pageable): Page<ProductEntity>

    // 5. Нечеткий поиск триграмм с устойчивостью к опечаткам (pg_trgm)
    @Query(
        value = "SELECT p.* FROM catalog.products p JOIN catalog.categories c ON p.category_id = c.id WHERE p.status = 'ACTIVE' AND c.is_active = true AND (p.search_vector @@ plainto_tsquery('russian', :query) OR public.similarity(p.title, :query) > :similarityThreshold) ORDER BY ts_rank(p.search_vector, plainto_tsquery('russian', :query)) DESC, public.similarity(p.title, :query) DESC",
        countQuery = "SELECT count(p.id) FROM catalog.products p JOIN catalog.categories c ON p.category_id = c.id WHERE p.status = 'ACTIVE' AND c.is_active = true AND (p.search_vector @@ plainto_tsquery('russian', :query) OR public.similarity(p.title, :query) > :similarityThreshold)",
        nativeQuery = true
    )
    fun searchActiveByTextAndTrigram(
        @Param("query") query: String,
        @Param("similarityThreshold") similarityThreshold: Double,
        pageable: Pageable
    ): Page<ProductEntity>
}
