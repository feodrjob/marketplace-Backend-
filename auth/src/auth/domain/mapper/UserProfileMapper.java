package auth.domain.mapper;

import auth.domain.model.UserModel;
import auth.domain.model.UserProfile;

import java.util.Objects;

public final class UserProfileMapper {

    public UserProfile toProfile(UserModel user) {
        Objects.requireNonNull(user, "user");
        return new UserProfile(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getFullName(),
                user.getPhone(),
                user.getCompanyName(),
                user.getInn(),
                user.getDescription(),
                user.getDepartment(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
