package auth.domain.service;

import auth.common.enums.Role;
import auth.domain.model.AuthSession;
import auth.domain.model.LogoutCommand;
import auth.domain.model.RefreshSessionCommand;
import auth.domain.model.RegisterBuyerCommand;
import auth.domain.model.RegisterSellerCommand;
import auth.domain.model.SignInCommand;
import auth.domain.model.TokenSettings;
import auth.domain.model.TokenUse;
import auth.domain.model.UserModel;
import auth.domain.model.UserProfile;
import auth.domain.support.AuthFixture;
import auth.exception.EmailAlreadyUsedException;
import auth.exception.InvalidCredentialsException;
import auth.exception.InvalidInnException;
import auth.exception.InvalidTokenException;
import auth.exception.UserNotFoundException;
import auth.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationServiceTest {

    private static final String PASSWORD = "password-1";
    private static final String BUYER_EMAIL = "buyer@mail.ru";
    private static final String SELLER_EMAIL = "seller@mail.ru";

    private AuthFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new AuthFixture();
    }

    @Test
    @DisplayName("US-1.1: покупатель создаётся с ролью BUYER, пароль хранится как хэш, сессия выдаётся сразу")
    void registerBuyerCreatesAccountAndSession() {
        AuthSession session = fixture.authentication.registerBuyer(
                new RegisterBuyerCommand("  Buyer@Mail.RU ", PASSWORD, "  Ivan  ", "  ")
        );

        UserProfile profile = session.getProfile();
        assertEquals("buyer@mail.ru", profile.getEmail());
        assertEquals(Role.BUYER, profile.getRole());
        assertEquals("Ivan", profile.getFullName());
        assertNull(profile.getPhone());
        assertNull(profile.getCompanyName());
        assertNull(profile.getInn());
        assertNotEquals(session.getAccessToken(), session.getRefreshToken());

        UserModel stored = fixture.users.findByEmail("buyer@mail.ru").orElseThrow();
        assertNotEquals(PASSWORD, stored.getPasswordHash());
        assertTrue(fixture.passwordEncoder.matches(PASSWORD, stored.getPasswordHash()));
        assertFalse(stored.toString().contains(PASSWORD));
        assertFalse(stored.toString().contains(stored.getPasswordHash()));
        assertEquals(Role.BUYER, fixture.tokens.verify(session.getAccessToken(), TokenUse.ACCESS).getRole());
        assertEquals(profile.getId(), fixture.tokens.verify(session.getRefreshToken(), TokenUse.REFRESH).getUserId());
    }

    @Test
    @DisplayName("US-1.1: повтор email между ролями даёт занятость и не создаёт вторую запись")
    void duplicateEmailIsRejectedAcrossRoles() {
        fixture.authentication.registerBuyer(new RegisterBuyerCommand(BUYER_EMAIL, PASSWORD, null, null));

        EmailAlreadyUsedException error = assertThrows(
                EmailAlreadyUsedException.class,
                () -> fixture.authentication.registerSeller(sellerCommand(BUYER_EMAIL, "7707083893"))
        );
        assertEquals("Пользователь с таким email уже зарегистрирован", error.getMessage());
        assertEquals(Role.BUYER, fixture.users.findByEmail(BUYER_EMAIL).orElseThrow().getRole());
    }

    @Test
    @DisplayName("Репозиторий не сохраняет второй id с тем же email, даже если сервис проверку пропустил")
    void repositoryRejectsDuplicateEmail() {
        AuthSession session = fixture.authentication.registerBuyer(
                new RegisterBuyerCommand(BUYER_EMAIL, PASSWORD, null, null)
        );
        UserModel stored = fixture.users.findById(session.getProfile().getId()).orElseThrow();
        UserModel otherId = UserModel.registerBuyer(
                UUID.randomUUID(),
                stored.getEmail(),
                stored.getPasswordHash(),
                null,
                null,
                stored.getCreatedAt()
        );

        assertThrows(EmailAlreadyUsedException.class, () -> fixture.users.save(otherId));
    }

    @Test
    @DisplayName("Пустой пароль и некорректный email отклоняются до создания записи")
    void registerBuyerRejectsBlankPasswordAndBadEmail() {
        assertThrows(ValidationException.class, () -> fixture.authentication.registerBuyer(
                new RegisterBuyerCommand(BUYER_EMAIL, "   ", null, null)
        ));
        assertThrows(ValidationException.class, () -> fixture.authentication.registerBuyer(
                new RegisterBuyerCommand("not-an-email", PASSWORD, null, null)
        ));
        assertTrue(fixture.users.findByEmail(BUYER_EMAIL).isEmpty());
    }

    @Test
    @DisplayName("US-1.2: продавец создаётся отдельным аккаунтом, ИНН обрезается, описание необязательно")
    void registerSellerCreatesSellerAccount() {
        AuthSession session = fixture.authentication.registerSeller(
                new RegisterSellerCommand(SELLER_EMAIL, PASSWORD, "  Лавка  ", " 7707083893 ", "  чай  ")
        );

        UserProfile profile = session.getProfile();
        assertEquals(Role.SELLER, profile.getRole());
        assertEquals("Лавка", profile.getCompanyName());
        assertEquals("7707083893", profile.getInn());
        assertEquals("чай", profile.getDescription());
        assertNull(profile.getFullName());
        assertNull(profile.getPhone());
        assertEquals(Role.SELLER, fixture.tokens.verify(session.getAccessToken(), TokenUse.ACCESS).getRole());
    }

    @Test
    @DisplayName("ИНН не из 10 или 12 цифр — ошибка формата, запись не создаётся")
    void registerSellerRejectsMalformedInn() {
        assertThrows(InvalidInnException.class, () -> fixture.authentication.registerSeller(
                sellerCommand(SELLER_EMAIL, "12345678901")
        ));
        assertThrows(InvalidInnException.class, () -> fixture.authentication.registerSeller(
                sellerCommand(SELLER_EMAIL, "770708389A")
        ));
        assertTrue(fixture.users.findByEmail(SELLER_EMAIL).isEmpty());
    }

    @Test
    @DisplayName("Пустые ИНН и название компании — ошибка заполнения, не ошибка формата ИНН")
    void registerSellerRejectsMissingRequiredFields() {
        assertThrows(ValidationException.class, () -> fixture.authentication.registerSeller(
                sellerCommand(SELLER_EMAIL, "   ")
        ));
        assertThrows(ValidationException.class, () -> fixture.authentication.registerSeller(
                new RegisterSellerCommand(SELLER_EMAIL, PASSWORD, "  ", "7707083893", null)
        ));
        assertTrue(fixture.users.findByEmail(SELLER_EMAIL).isEmpty());
    }

    @Test
    @DisplayName("US-1.3: вход по email без учёта регистра, неверный пароль и чужая почта неотличимы")
    void signInAcceptsAccountAndHidesWhichFieldFailed() {
        fixture.authentication.registerBuyer(new RegisterBuyerCommand(BUYER_EMAIL, PASSWORD, "Ivan", null));

        AuthSession session = fixture.authentication.signIn(new SignInCommand("BUYER@MAIL.RU", PASSWORD));
        assertEquals(BUYER_EMAIL, session.getProfile().getEmail());

        assertThrows(InvalidCredentialsException.class, () -> fixture.authentication.signIn(
                new SignInCommand(BUYER_EMAIL, "other-password")
        ));
        assertThrows(InvalidCredentialsException.class, () -> fixture.authentication.signIn(
                new SignInCommand("missing@mail.ru", PASSWORD)
        ));
    }

    @Test
    @DisplayName("Refresh выпускает новую пару по текущей записи и не принимает access-токен")
    void refreshRotatesSessionAndRejectsAccessToken() {
        AuthSession original = fixture.authentication.registerBuyer(
                new RegisterBuyerCommand(BUYER_EMAIL, PASSWORD, null, null)
        );

        AuthSession refreshed = fixture.authentication.refresh(new RefreshSessionCommand(original.getRefreshToken()));

        assertNotEquals(original.getAccessToken(), refreshed.getAccessToken());
        assertEquals(original.getProfile().getId(), refreshed.getProfile().getId());
        assertEquals(Role.BUYER, fixture.tokens.verify(refreshed.getAccessToken(), TokenUse.ACCESS).getRole());
        // Старый access ещё подписан: без хранилища сессий подпись не отзывается до exp.
        fixture.tokens.verify(original.getAccessToken(), TokenUse.ACCESS);
        assertThrows(InvalidTokenException.class, () -> fixture.authentication.refresh(
                new RefreshSessionCommand(original.getAccessToken())
        ));
    }

    @Test
    @DisplayName("Refresh не восстанавливает сессию удалённой учётной записи")
    void refreshRejectsMissingUser() {
        AuthSession session = fixture.authentication.registerBuyer(
                new RegisterBuyerCommand(BUYER_EMAIL, PASSWORD, null, null)
        );
        fixture.users.remove(session.getProfile().getId());

        assertThrows(UserNotFoundException.class, () -> fixture.authentication.refresh(
                new RefreshSessionCommand(session.getRefreshToken())
        ));
    }

    @Test
    @DisplayName("Access живёт 15 минут, refresh — 30 суток; чужой мусор и короткий ключ отвергаются")
    void tokensExpireBySpecAndRejectGarbage() {
        AuthSession session = fixture.authentication.registerBuyer(
                new RegisterBuyerCommand(BUYER_EMAIL, PASSWORD, null, null)
        );

        // JWT хранит exp в секундах, поэтому границу берём с запасом в одну секунду.
        fixture.clock.advance(TokenSettings.ACCESS_TTL.minusSeconds(1));
        fixture.tokens.verify(session.getAccessToken(), TokenUse.ACCESS);

        fixture.clock.advance(Duration.ofSeconds(2));
        assertThrows(InvalidTokenException.class, () -> fixture.tokens.verify(session.getAccessToken(), TokenUse.ACCESS));
        fixture.tokens.verify(session.getRefreshToken(), TokenUse.REFRESH);

        fixture.clock.advance(TokenSettings.REFRESH_TTL);
        assertThrows(InvalidTokenException.class, () -> fixture.authentication.refresh(
                new RefreshSessionCommand(session.getRefreshToken())
        ));
        assertThrows(InvalidTokenException.class, () -> fixture.tokens.verify("not-a-jwt", TokenUse.ACCESS));
        assertThrows(IllegalArgumentException.class, () -> TokenSettings.marketplaceDefaults(
                "short".getBytes(StandardCharsets.UTF_8)
        ));
    }

    @Test
    @DisplayName("Logout принимает живой access, не удаляет аккаунт и не пускает refresh вместо access")
    void logoutChecksAccessTokenAndKeepsAccount() {
        AuthSession session = fixture.authentication.registerBuyer(
                new RegisterBuyerCommand(BUYER_EMAIL, PASSWORD, null, null)
        );

        fixture.authentication.logout(new LogoutCommand(session.getAccessToken()));
        fixture.authentication.logout(new LogoutCommand(session.getAccessToken()));

        assertTrue(fixture.users.findById(session.getProfile().getId()).isPresent());
        assertThrows(InvalidTokenException.class, () -> fixture.authentication.logout(
                new LogoutCommand(session.getRefreshToken())
        ));
        assertThrows(InvalidTokenException.class, () -> fixture.authentication.logout(new LogoutCommand("  ")));
    }

    private static RegisterSellerCommand sellerCommand(String email, String inn) {
        return new RegisterSellerCommand(email, PASSWORD, "Лавка", inn, null);
    }
}
