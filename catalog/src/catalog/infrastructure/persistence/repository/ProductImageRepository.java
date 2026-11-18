package catalog.infrastructure.persistence.repository;

import catalog.infrastructure.persistence.entity.ProductImageEntity;
import catalog.infrastructure.persistence.entity.ProductImageId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImageEntity, ProductImageId> {

    List<ProductImageEntity> findByIdProductIdOrderBySortOrderAsc(UUID productId);

    boolean existsByIdMediaId(UUID mediaId);

    long countByIdMediaId(UUID mediaId);
}
