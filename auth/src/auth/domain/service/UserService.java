package auth.domain.service;

import auth.domain.mapper.UserProfileMapper;
import auth.domain.model.UpdateProfileCommand;
import auth.domain.model.UserModel;
import auth.domain.model.UserProfile;
import auth.domain.repository.UserModelRepository;

import java.time.Clock;
import java.util.Objects;

// Чтение и правка своего профиля (GET /users/me, PATCH /users/me)
public final class UserService {

    private final UserModelRepository users;
    private final AuthenticatedUserLoader authenticatedUsers;
    private final UserProfileMapper profiles;
    private final Clock clock;

    public UserService(
            UserModelRepository users,
            TokenService tokenService,
            Clock clock,
            UserProfileMapper profiles
    ) {
        this.users = Objects.requireNonNull(users, "users");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.authenticatedUsers = new AuthenticatedUserLoader(users, tokenService);
    }

    public UserProfile getProfile(String accessToken) {
        return profiles.toProfile(authenticatedUsers.load(accessToken));
    }

    public UserProfile updateProfile(UpdateProfileCommand command) {
        Objects.requireNonNull(command, "command");
        UserModel current = authenticatedUsers.load(command.getAccessToken());
        UserModel updated = current.apply(command.getPatch(), clock.instant());
        if (updated != current) {
            updated = users.save(updated);
        }
        return profiles.toProfile(updated);
    }
}
