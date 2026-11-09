package auth.domain.service;

import auth.domain.model.TokenClaims;
import auth.domain.model.TokenUse;
import auth.domain.model.UserModel;
import auth.domain.repository.UserModelRepository;
import auth.exception.UserNotFoundException;

import java.util.Objects;

final class AuthenticatedUserLoader {

    private final UserModelRepository users;
    private final TokenService tokenService;

    AuthenticatedUserLoader(UserModelRepository users, TokenService tokenService) {
        this.users = Objects.requireNonNull(users, "users");
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService");
    }

    UserModel load(String accessToken) {
        TokenClaims claims = tokenService.verify(accessToken, TokenUse.ACCESS);
        return users.findById(claims.getUserId())
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден"));
    }
}
