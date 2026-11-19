package catalog.infrastructure.persistence.repository

import catalog.common.enums.ProductStatus
import catalog.infrastructure.persistence.entity.CategoryEntity
import catalog.infrastructure.persistence.entity.ProductEntity
import jakarta.persistence.criteria.Join
import org.springframework.data.jpa.domain.Specification
import java.util.UUID

object ProductSpecifications {

    fun hasStatus(status: ProductStatus?): Specification<ProductEntity> {
        if (status == null) return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, cb ->
            cb.equal(root.get<ProductStatus>("status"), status)
        }
    }

    fun isCategoryActive(): Specification<ProductEntity> {
        return Specification { root, _, cb ->
            val categoryJoin: Join<ProductEntity, CategoryEntity> = root.join("category")
            cb.isTrue(categoryJoin.get("isActive"))
        }
    }

    fun hasCategorySlug(slug: String?): Specification<ProductEntity> {
        if (slug.isNullOrBlank()) return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, cb ->
            val categoryJoin: Join<ProductEntity, CategoryEntity> = root.join("category")
            cb.equal(categoryJoin.get<String>("slug"), slug)
        }
    }

    fun hasCategorySlugIn(slugs: Collection<String>?): Specification<ProductEntity> {
        if (slugs.isNullOrEmpty()) return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, _ ->
            val categoryJoin: Join<ProductEntity, CategoryEntity> = root.join("category")
            categoryJoin.get<String>("slug").`in`(slugs)
        }
    }

    fun hasCategoryId(categoryId: UUID?): Specification<ProductEntity> {
        if (categoryId == null) return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, cb ->
            cb.equal(root.get<UUID>("categoryId"), categoryId)
        }
    }

    fun hasCategoryIdIn(categoryIds: Collection<UUID>?): Specification<ProductEntity> {
        if (categoryIds.isNullOrEmpty()) return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, _ ->
            root.get<UUID>("categoryId").`in`(categoryIds)
        }
    }

    fun hasSellerId(sellerId: UUID?): Specification<ProductEntity> {
        if (sellerId == null) return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, cb ->
            cb.equal(root.get<UUID>("sellerId"), sellerId)
        }
    }

    fun priceBetween(minPrice: Long?, maxPrice: Long?): Specification<ProductEntity> {
        if (minPrice == null && maxPrice == null) return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, cb ->
            when {
                minPrice != null && maxPrice != null -> cb.between(root.get("price"), minPrice, maxPrice)
                minPrice != null -> cb.greaterThanOrEqualTo(root.get("price"), minPrice)
                maxPrice != null -> cb.lessThanOrEqualTo(root.get("price"), maxPrice)
                else -> cb.conjunction()
            }
        }
    }

    fun storefrontActiveProducts(
        categorySlug: String? = null,
        categorySlugs: Collection<String>? = null,
        categoryId: UUID? = null,
        categoryIds: Collection<UUID>? = null,
        sellerId: UUID? = null,
        minPrice: Long? = null,
        maxPrice: Long? = null
    ): Specification<ProductEntity> {
        return Specification.where(hasStatus(ProductStatus.ACTIVE))
            .and(isCategoryActive())
            .and(hasCategorySlug(categorySlug))
            .and(hasCategorySlugIn(categorySlugs))
            .and(hasCategoryId(categoryId))
            .and(hasCategoryIdIn(categoryIds))
            .and(hasSellerId(sellerId))
            .and(priceBetween(minPrice, maxPrice))
    }
}
