package catalog.infrastructure.persistence.repository;

import catalog.common.enums.ProductStatus;
import catalog.infrastructure.persistence.entity.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, UUID>, JpaSpecificationExecutor<ProductEntity> {

    Optional<ProductEntity> findByIdAndStatus(UUID id, ProductStatus status);

    Page<ProductEntity> findAllByStatus(ProductStatus status, Pageable pageable);

    Page<ProductEntity> findAllByStatusAndCategoryId(ProductStatus status, UUID categoryId, Pageable pageable);

    Page<ProductEntity> findAllByStatusAndCategoryIdIn(ProductStatus status, Collection<UUID> categoryIds, Pageable pageable);

    Page<ProductEntity> findAllByStatusAndCategorySlug(ProductStatus status, String slug, Pageable pageable);

    Page<ProductEntity> findAllByStatusAndCategorySlugIn(ProductStatus status, Collection<String> slugs, Pageable pageable);

    Page<ProductEntity> findAllBySellerId(UUID sellerId, Pageable pageable);

    Page<ProductEntity> findAllBySellerIdAndStatus(UUID sellerId, ProductStatus status, Pageable pageable);

    Page<ProductEntity> findAllBySellerIdAndCategoryId(UUID sellerId, UUID categoryId, Pageable pageable);

    Page<ProductEntity> findAllBySellerIdAndCategorySlug(UUID sellerId, String slug, Pageable pageable);

    boolean existsByIdAndStatus(UUID id, ProductStatus status);

    boolean existsBySellerId(UUID sellerId);

    boolean existsByCategoryId(UUID categoryId);

    @Query(value = "SELECT * FROM catalog.products WHERE status = 'ACTIVE' AND search_vector @@ plainto_tsquery('russian', :query)",
           countQuery = "SELECT count(*) FROM catalog.products WHERE status = 'ACTIVE' AND search_vector @@ plainto_tsquery('russian', :query)",
           nativeQuery = true)
    Page<ProductEntity> searchActiveByText(@Param("query") String query, Pageable pageable);

    @Query(value = "SELECT * FROM catalog.products WHERE status = 'ACTIVE' AND (search_vector @@ plainto_tsquery('russian', :query) OR public.similarity(title, :query) > :similarityThreshold) ORDER BY ts_rank(search_vector, plainto_tsquery('russian', :query)) DESC, public.similarity(title, :query) DESC",
           countQuery = "SELECT count(*) FROM catalog.products WHERE status = 'ACTIVE' AND (search_vector @@ plainto_tsquery('russian', :query) OR public.similarity(title, :query) > :similarityThreshold)",
           nativeQuery = true)
    Page<ProductEntity> searchActiveByTextAndTrigram(@Param("query") String query, @Param("similarityThreshold") double similarityThreshold, Pageable pageable);
}
