package auth.domain.support;

import auth.domain.mapper.UserProfileMapper;
import auth.domain.model.TokenSettings;
import auth.domain.service.AuthenticationService;
import auth.domain.service.EmailPolicy;
import auth.domain.service.IdGenerator;
import auth.domain.service.InnPolicy;
import auth.domain.service.JwtTokenService;
import auth.domain.service.PasswordPolicy;
import auth.domain.service.TokenService;
import auth.domain.service.UserService;
import auth.domain.service.UuidIdGenerator;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

// Собирает доменные сервисы на памяти и фиксированных часах
public final class AuthFixture {

    public static final byte[] TEST_SECRET =
            "marketplace-auth-test-secret-key!".getBytes(StandardCharsets.UTF_8);

    public final MutableClock clock = new MutableClock(Instant.parse("2026-11-09T12:00:00Z"));
    public final InMemoryUserModelRepository users = new InMemoryUserModelRepository();
    public final ReversiblePasswordEncoder passwordEncoder = new ReversiblePasswordEncoder();
    public final TokenService tokens;
    public final AuthenticationService authentication;
    public final UserService userService;

    public AuthFixture() {
        IdGenerator ids = new UuidIdGenerator();
        // jti берётся из генератора, иначе повторный выпуск в ту же секунду даёт ту же подпись.
        this.tokens = new JwtTokenService(TokenSettings.marketplaceDefaults(TEST_SECRET), clock, ids);
        UserProfileMapper profiles = new UserProfileMapper();
        this.authentication = new AuthenticationService(
                users,
                passwordEncoder,
                tokens,
                ids,
                clock,
                new EmailPolicy(),
                new PasswordPolicy(),
                new InnPolicy(),
                profiles
        );
        this.userService = new UserService(users, tokens, clock, profiles);
    }
}
