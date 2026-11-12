package auth.infrastructure.persistence.repository;

import auth.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM UserEntity u JOIN u.sellerProfile s WHERE s.inn = :inn")
    Optional<UserEntity> findBySellerInn(@Param("inn") String inn);

    @Query("SELECT COUNT(u) > 0 FROM UserEntity u JOIN u.sellerProfile s WHERE s.inn = :inn")
    boolean existsBySellerInn(@Param("inn") String inn);
}
