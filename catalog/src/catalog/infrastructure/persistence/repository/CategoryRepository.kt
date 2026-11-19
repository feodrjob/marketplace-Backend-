package catalog.infrastructure.persistence.repository

import catalog.infrastructure.persistence.entity.CategoryEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface CategoryRepository : JpaRepository<CategoryEntity, UUID>, JpaSpecificationExecutor<CategoryEntity> {

    fun findBySlug(slug: String): Optional<CategoryEntity>

    fun findBySlugAndIsActiveTrue(slug: String): Optional<CategoryEntity>

    fun existsBySlug(slug: String): Boolean

    fun findAllByIsActiveTrueOrderBySortOrderAscNameAsc(): List<CategoryEntity>

    fun findAllByParentIdIsNullAndIsActiveTrueOrderBySortOrderAscNameAsc(): List<CategoryEntity>

    fun findAllByParentIdAndIsActiveTrueOrderBySortOrderAscNameAsc(parentId: UUID): List<CategoryEntity>

    fun findAllByParentId(parentId: UUID): List<CategoryEntity>

    fun findAllByOrderBySortOrderAscNameAsc(): List<CategoryEntity>

    fun existsByParentId(parentId: UUID): Boolean
}
