package auth.domain.support;

import auth.domain.model.UserModel;
import auth.domain.repository.UserModelRepository;
import auth.exception.EmailAlreadyUsedException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// тест 2.1 и 2.2
public final class InMemoryUserModelRepository implements UserModelRepository {

    private final Map<UUID, UserModel> usersById = new ConcurrentHashMap<>();

    @Override
    public Optional<UserModel> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(usersById.get(id));
    }

    @Override
    public Optional<UserModel> findByEmail(String email) {
        return usersById.values().stream()
                .filter(user -> user.getEmail().equals(email))
                .findFirst();
    }

    @Override
    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }

    @Override
    public UserModel save(UserModel user) {
        findByEmail(user.getEmail())
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    throw new EmailAlreadyUsedException("Пользователь с таким email уже зарегистрирован");
                });
        usersById.put(user.getId(), user);
        return user;
    }

    public void remove(UUID id) {
        usersById.remove(id);
    }
}
