package auth.config;

import auth.domain.mapper.UserProfileMapper;
import auth.domain.model.TokenSettings;
import auth.domain.model.UserModel;
import auth.domain.repository.UserModelRepository;
import auth.domain.service.AuthenticationService;
import auth.domain.service.EmailPolicy;
import auth.domain.service.IdGenerator;
import auth.domain.service.InnPolicy;
import auth.domain.service.JwtTokenService;
import auth.domain.service.PasswordPolicy;
import auth.domain.service.TokenService;
import auth.domain.service.UserService;
import auth.domain.service.UuidIdGenerator;
import auth.exception.EmailAlreadyUsedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class AuthDomainConfig {

    @Bean
    @ConditionalOnMissingBean
    public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
    }

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public IdGenerator idGenerator() {
        return new UuidIdGenerator();
    }

    @Bean
    @ConditionalOnMissingBean
    public EmailPolicy emailPolicy() {
        return new EmailPolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    public PasswordPolicy passwordPolicy() {
        return new PasswordPolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    public InnPolicy innPolicy() {
        return new InnPolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    public UserProfileMapper userProfileMapper() {
        return new UserProfileMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenSettings tokenSettings(
            @Value("${auth.jwt.secret:marketplace-auth-default-secret-key-for-development-32bytes!}") String secretKey
    ) {
        return TokenSettings.marketplaceDefaults(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenService tokenService(TokenSettings settings, Clock clock, IdGenerator idGenerator) {
        return new JwtTokenService(settings, clock, idGenerator);
    }

    @Bean
    @ConditionalOnMissingBean
    public UserModelRepository userModelRepository() {
        // In-memory fallback if JPA UserModelRepositoryImpl is not wired yet
        return new UserModelRepository() {
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
                if (email == null) {
                    return Optional.empty();
                }
                return usersById.values().stream()
                        .filter(u -> email.equalsIgnoreCase(u.getEmail()))
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
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthenticationService authenticationService(
            UserModelRepository users,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            IdGenerator idGenerator,
            Clock clock,
            EmailPolicy emailPolicy,
            PasswordPolicy passwordPolicy,
            InnPolicy innPolicy,
            UserProfileMapper profiles
    ) {
        return new AuthenticationService(
                users,
                passwordEncoder,
                tokenService,
                idGenerator,
                clock,
                emailPolicy,
                passwordPolicy,
                innPolicy,
                profiles
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public UserService userService(
            UserModelRepository users,
            TokenService tokenService,
            Clock clock,
            UserProfileMapper profiles
    ) {
        return new UserService(users, tokenService, clock, profiles);
    }
}
