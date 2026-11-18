package catalog.infrastructure.persistence.repository;

import catalog.infrastructure.persistence.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID>, JpaSpecificationExecutor<CategoryEntity> {

    Optional<CategoryEntity> findBySlug(String slug);

    Optional<CategoryEntity> findBySlugAndIsActiveTrue(String slug);

    boolean existsBySlug(String slug);

    List<CategoryEntity> findAllByIsActiveTrueOrderBySortOrderAscNameAsc();

    List<CategoryEntity> findAllByParentIdIsNullAndIsActiveTrueOrderBySortOrderAscNameAsc();

    List<CategoryEntity> findAllByParentIdAndIsActiveTrueOrderBySortOrderAscNameAsc(UUID parentId);

    List<CategoryEntity> findAllByParentId(UUID parentId);

    List<CategoryEntity> findAllByOrderBySortOrderAscNameAsc();

    boolean existsByParentId(UUID parentId);
}
