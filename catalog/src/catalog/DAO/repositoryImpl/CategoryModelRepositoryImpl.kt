package catalog.DAO.repositoryImpl

import catalog.DAO.mapper.CategoryEntityMapper
import catalog.domain.model.CategoryModel
import catalog.domain.repository.CategoryRepository
import catalog.infrastructure.persistence.repository.CategoryRepository as JpaCategoryRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Подключает доменное хранилище категорий к JPA.
 */
@Repository
@Transactional(readOnly = true)
class CategoryModelRepositoryImpl(
    private val entities: JpaCategoryRepository,
    private val mapper: CategoryEntityMapper,
) : CategoryRepository {

    // Загружает все категории из БД.
    override fun findAll(): List<CategoryModel> {
        return entities.findAll().map { mapper.toDomain(it) }
    }

    // Загружает категорию по идентификатору.
    override fun findById(id: UUID): CategoryModel? {
        return entities.findById(id)
            .map { mapper.toDomain(it) }
            .orElse(null)
    }
}