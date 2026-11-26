package catalog.infrastructure.persistence.adapter

import catalog.domain.model.CategoryModel
import catalog.domain.repository.CategoryRepository
import catalog.infrastructure.persistence.entity.CategoryEntity
import catalog.infrastructure.persistence.repository.CategoryRepository as JpaCategoryRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/*
* Связывает доменный репозиторий категорий с PostgreSQL
* */
@Repository
@Transactional(readOnly = true)
class CategoryRepositoryAdapter(
    private val entities: JpaCategoryRepository,
) : CategoryRepository {
    override fun findAll(): List<CategoryModel> = entities.findAll().map { toModel(it) }

    override fun findById(id: UUID): CategoryModel? = entities.findById(id).orElse(null)?.let { toModel(it) }

    private fun toModel(entity: CategoryEntity): CategoryModel = CategoryModel(
        id = checkNotNull(entity.id),
        parentId = entity.parentId,
        name = entity.name,
        slug = entity.slug,
        sortOrder = entity.sortOrder,
        active = entity.isActive,
    )
}
