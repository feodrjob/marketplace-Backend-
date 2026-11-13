package auth.domain.service;

import auth.domain.mapper.UserProfileMapper;
import auth.domain.model.AuthSession;
import auth.domain.model.LogoutCommand;
import auth.domain.model.RefreshSessionCommand;
import auth.domain.model.RegisterBuyerCommand;
import auth.domain.model.RegisterSellerCommand;
import auth.domain.model.SignInCommand;
import auth.domain.model.TextValues;
import auth.domain.model.TokenClaims;
import auth.domain.model.TokenPair;
import auth.domain.model.TokenUse;
import auth.domain.model.UserModel;
import auth.domain.repository.UserModelRepository;
import auth.exception.EmailAlreadyUsedException;
import auth.exception.InvalidCredentialsException;
import auth.exception.UserNotFoundException;
import auth.exception.ValidationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.Objects;

public final class AuthenticationService {

    private static final String DUPLICATE_EMAIL = "Пользователь с таким email уже зарегистрирован";
    private static final String BAD_CREDENTIALS = "Неверный email или пароль";
    private static final String BLANK_COMPANY = "Название компании обязательно";

    private static final String TIMING_EQUALIZER = "timing-equalizer";

    private final UserModelRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final EmailPolicy emailPolicy;
    private final PasswordPolicy passwordPolicy;
    private final InnPolicy innPolicy;
    private final UserProfileMapper profiles;
    private final String rejectionHash;

    public AuthenticationService(
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
        this.users = Objects.requireNonNull(users, "users");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.emailPolicy = Objects.requireNonNull(emailPolicy, "emailPolicy");
        this.passwordPolicy = Objects.requireNonNull(passwordPolicy, "passwordPolicy");
        this.innPolicy = Objects.requireNonNull(innPolicy, "innPolicy");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.rejectionHash = passwordEncoder.encode(TIMING_EQUALIZER);
    }

    // Регистрация покупателя
    public AuthSession registerBuyer(RegisterBuyerCommand command) {
        Objects.requireNonNull(command, "command");
        String email = emailPolicy.normalize(command.getEmail());
        passwordPolicy.check(command.getPassword());
        ensureEmailIsFree(email);
        UserModel user = UserModel.registerBuyer(
                idGenerator.nextId(),
                email,
                passwordEncoder.encode(command.getPassword()),
                TextValues.trimToNull(command.getFullName()),
                TextValues.trimToNull(command.getPhone()),
                clock.instant()
        );
        return openSession(users.save(user));
    }

    // Регистрация продавца
    public AuthSession registerSeller(RegisterSellerCommand command) {
        Objects.requireNonNull(command, "command");
        String email = emailPolicy.normalize(command.getEmail());
        passwordPolicy.check(command.getPassword());
        String companyName = TextValues.trimToNull(command.getCompanyName());
        if (companyName == null) {
            throw new ValidationException(BLANK_COMPANY);
        }
        String inn = innPolicy.canonicalize(command.getInn());
        ensureEmailIsFree(email);
        UserModel user = UserModel.registerSeller(
                idGenerator.nextId(),
                email,
                passwordEncoder.encode(command.getPassword()),
                companyName,
                inn,
                TextValues.trimToNull(command.getDescription()),
                clock.instant()
        );
        return openSession(users.save(user));
    }

    // Вход
    public AuthSession signIn(SignInCommand command) {
        Objects.requireNonNull(command, "command");
        passwordPolicy.check(command.getPassword());
        String email = emailPolicy.normalize(command.getEmail());
        UserModel user = users.findByEmail(email).orElse(null);
        String hash = user == null ? rejectionHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(command.getPassword(), hash);
        if (user == null || !passwordMatches) {
            throw new InvalidCredentialsException(BAD_CREDENTIALS);
        }
        return openSession(user);
    }

    //Новая пара токенов по refresh
    public AuthSession refresh(RefreshSessionCommand command) {
        Objects.requireNonNull(command, "command");
        TokenClaims claims = tokenService.verify(command.getRefreshToken(), TokenUse.REFRESH);
        UserModel user = users.findById(claims.getUserId())
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден"));
        return openSession(user);
    }

    // Выход
    public void logout(LogoutCommand command) {
        Objects.requireNonNull(command, "command");
        tokenService.verify(command.getAccessToken(), TokenUse.ACCESS);
    }

    private void ensureEmailIsFree(String email) {
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyUsedException(DUPLICATE_EMAIL);
        }
    }

    private AuthSession openSession(UserModel user) {
        TokenPair tokens = tokenService.issue(user);
        return new AuthSession(tokens.getAccessToken(), tokens.getRefreshToken(), profiles.toProfile(user));
    }
}
