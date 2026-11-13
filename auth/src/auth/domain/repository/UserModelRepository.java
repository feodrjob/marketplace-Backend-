package auth.domain.repository;

import auth.domain.model.UserModel;

import java.util.Optional;
import java.util.UUID;

// Порт хранения учётных записей
public interface UserModelRepository {

    Optional<UserModel> findById(UUID id);

    Optional<UserModel> findByEmail(String email);

    boolean existsByEmail(String email);

    UserModel save(UserModel user);
}
