package auth.domain.service;

import auth.common.enums.Role;
import auth.domain.model.AuthSession;
import auth.domain.model.ProfilePatch;
import auth.domain.model.RegisterBuyerCommand;
import auth.domain.model.RegisterSellerCommand;
import auth.domain.model.TokenUse;
import auth.domain.model.UpdateProfileCommand;
import auth.domain.model.UserModel;
import auth.domain.model.UserProfile;
import auth.domain.support.AuthFixture;
import auth.exception.InvalidTokenException;
import auth.exception.ProfileUpdateNotAllowedException;
import auth.exception.UserNotFoundException;
import auth.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserServiceTest {

    private static final String PASSWORD = "password-1";
    private static final String BUYER_EMAIL = "buyer@mail.ru";
    private static final String SELLER_EMAIL = "seller@mail.ru";
    private static final String INN = "7707083893";

    private AuthFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new AuthFixture();
    }

    @Test
    @DisplayName("GET /users/me читает запись из хранилища и не отдаёт хэш пароля")
    void getProfileReturnsAccountWithoutPassword() throws Exception {
        AuthSession session = registerBuyer();

        UserProfile profile = fixture.userService.getProfile(session.getAccessToken());

        assertEquals(session.getProfile().getId(), profile.getId());
        assertEquals(BUYER_EMAIL, profile.getEmail());
        assertEquals(Role.BUYER, profile.getRole());
        assertThrows(NoSuchMethodException.class, () -> UserProfile.class.getMethod("getPasswordHash"));
        assertThrows(InvalidTokenException.class, () -> fixture.userService.getProfile(session.getRefreshToken()));
    }

    @Test
    @DisplayName("Покупатель меняет имя и телефон, чужие поля и пустое имя очищаются по правилам роли")
    void buyerUpdatesOwnContactsOnly() {
        AuthSession session = registerBuyer();
        fixture.clock.advance(Duration.ofMinutes(1));

        UserProfile updated = fixture.userService.updateProfile(new UpdateProfileCommand(
                session.getAccessToken(),
                ProfilePatch.builder().fullName("  Anna  ").phone(" +7999 ").build()
        ));

        assertEquals("Anna", updated.getFullName());
        assertEquals("+7999", updated.getPhone());
        assertEquals(BUYER_EMAIL, updated.getEmail());
        assertEquals(Role.BUYER, updated.getRole());
        assertEquals(session.getProfile().getCreatedAt(), updated.getCreatedAt());
        assertEquals(fixture.clock.instant(), updated.getUpdatedAt());

        assertThrows(ProfileUpdateNotAllowedException.class, () -> fixture.userService.updateProfile(
                new UpdateProfileCommand(session.getAccessToken(), ProfilePatch.builder().inn(INN).build())
        ));
        assertThrows(ProfileUpdateNotAllowedException.class, () -> fixture.userService.updateProfile(
                new UpdateProfileCommand(
                        session.getAccessToken(),
                        ProfilePatch.builder().companyName("Чужая лавка").build()
                )
        ));
    }

    @Test
    @DisplayName("Пустая правка покупателя не двигает updatedAt")
    void emptyBuyerPatchDoesNotTouchTimestamp() {
        AuthSession session = registerBuyer();
        Instant updatedAt = session.getProfile().getUpdatedAt();
        fixture.clock.advance(Duration.ofMinutes(1));

        UserProfile same = fixture.userService.updateProfile(new UpdateProfileCommand(
                session.getAccessToken(),
                ProfilePatch.builder().build()
        ));

        assertEquals(updatedAt, same.getUpdatedAt());
    }

    @Test
    @DisplayName("Продавец меняет название и описание, тот же ИНН допустим, другой ИНН и телефон нет")
    void sellerUpdatesShopTextAndKeepsInn() {
        AuthSession session = fixture.authentication.registerSeller(
                new RegisterSellerCommand(SELLER_EMAIL, PASSWORD, "Лавка", INN, "старое")
        );
        fixture.clock.advance(Duration.ofMinutes(1));

        UserProfile sameInn = fixture.userService.updateProfile(new UpdateProfileCommand(
                session.getAccessToken(),
                ProfilePatch.builder().inn(" " + INN + " ").companyName(" Новая лавка ").description(" ").build()
        ));
        assertEquals("Новая лавка", sameInn.getCompanyName());
        assertNull(sameInn.getDescription());
        assertEquals(INN, sameInn.getInn());

        assertThrows(ProfileUpdateNotAllowedException.class, () -> fixture.userService.updateProfile(
                new UpdateProfileCommand(session.getAccessToken(), ProfilePatch.builder().inn("500100732259").build())
        ));
        assertThrows(ProfileUpdateNotAllowedException.class, () -> fixture.userService.updateProfile(
                new UpdateProfileCommand(session.getAccessToken(), ProfilePatch.builder().phone("+7999").build())
        ));
        assertThrows(ValidationException.class, () -> fixture.userService.updateProfile(
                new UpdateProfileCommand(session.getAccessToken(), ProfilePatch.builder().companyName("  ").build())
        ));
        assertEquals(INN, fixture.users.findByEmail(SELLER_EMAIL).orElseThrow().getInn());
    }

    @Test
    @DisplayName("Администратор читает department и не правит профиль через пользовательский сценарий")
    void adminProfileIsReadOnly() {
        UserModel admin = UserModel.restore(
                java.util.UUID.randomUUID(),
                "admin@mail.ru",
                fixture.passwordEncoder.encode("service-password"),
                Role.ADMIN,
                null,
                null,
                null,
                null,
                null,
                "moderation",
                fixture.clock.instant(),
                fixture.clock.instant()
        );
        fixture.users.save(admin);
        String access = fixture.tokens.issue(admin).getAccessToken();

        UserProfile profile = fixture.userService.getProfile(access);
        assertEquals(Role.ADMIN, profile.getRole());
        assertEquals("moderation", profile.getDepartment());
        assertNull(profile.getInn());

        assertThrows(ProfileUpdateNotAllowedException.class, () -> fixture.userService.updateProfile(
                new UpdateProfileCommand(access, ProfilePatch.builder().fullName("Boss").build())
        ));
    }

    @Test
    @DisplayName("Профиль не открывается, если учётную запись уже убрали")
    void getProfileRejectsMissingUser() {
        AuthSession session = registerBuyer();
        fixture.users.remove(session.getProfile().getId());

        assertThrows(UserNotFoundException.class, () -> fixture.userService.getProfile(session.getAccessToken()));
    }

    @Test
    @DisplayName("Access-токен другой операции не читает профиль")
    void profileRequiresAccessToken() {
        AuthSession session = registerBuyer();
        assertThrows(InvalidTokenException.class, () -> fixture.userService.getProfile(null));
        fixture.tokens.verify(session.getAccessToken(), TokenUse.ACCESS);
    }

    private AuthSession registerBuyer() {
        return fixture.authentication.registerBuyer(
                new RegisterBuyerCommand(BUYER_EMAIL, PASSWORD, "Ivan", "+7000")
        );
    }
}
