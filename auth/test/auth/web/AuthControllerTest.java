package auth.web;

import auth.common.enums.Role;
import auth.domain.model.AuthSession;
import auth.domain.model.LogoutCommand;
import auth.domain.model.RefreshSessionCommand;
import auth.domain.model.RegisterBuyerCommand;
import auth.domain.model.RegisterSellerCommand;
import auth.domain.model.SignInCommand;
import auth.domain.model.UserProfile;
import auth.domain.service.AuthenticationService;
import auth.exception.EmailAlreadyUsedException;
import auth.exception.InvalidCredentialsException;
import auth.exception.InvalidTokenException;
import auth.web.controller.AuthController;
import auth.web.exception.GlobalExceptionHandler;
import auth.web.mapper.AuthDtoMapper;
import auth.web.security.CookieHelper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthenticationService authenticationService;

    private final AuthDtoMapper mapper = new AuthDtoMapper();
    private final CookieHelper cookieHelper = new CookieHelper();

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(authenticationService, mapper, cookieHelper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private UserProfile createBuyerProfile(UUID id, String email) {
        return new UserProfile(
                id,
                email,
                Role.BUYER,
                "Anna Ivanova",
                "+79990000001",
                null,
                null,
                null,
                null,
                Instant.parse("2026-11-13T12:00:00Z"),
                Instant.parse("2026-11-13T12:00:00Z")
        );
    }

    private UserProfile createSellerProfile(UUID id, String email) {
        return new UserProfile(
                id,
                email,
                Role.SELLER,
                null,
                null,
                "Romashka LLC",
                "7701234560",
                "Flower shop",
                null,
                Instant.parse("2026-11-13T12:00:00Z"),
                Instant.parse("2026-11-13T12:00:00Z")
        );
    }

    @Nested
    @DisplayName("2.1 POST /api/v1/auth/register/buyer")
    class RegisterBuyerTests {

        @Test
        @DisplayName("201: успешная регистрация покупателя возвращает токены, профиль и cookie")
        void shouldRegisterBuyerSuccessfully() throws Exception {
            UUID userId = UUID.randomUUID();
            UserProfile profile = createBuyerProfile(userId, "buyer@example.test");
            AuthSession session = new AuthSession("access.jwt.token", "refresh.jwt.token", profile);

            when(authenticationService.registerBuyer(any(RegisterBuyerCommand.class))).thenReturn(session);

            String requestJson = """
                    {
                        "email": "buyer@example.test",
                        "password": "strong-password-123",
                        "fullName": "Anna Ivanova",
                        "phone": "+79990000001"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/register/buyer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=refresh.jwt.token")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")))
                    .andExpect(jsonPath("$.accessToken", is("access.jwt.token")))
                    .andExpect(jsonPath("$.user.id", is(userId.toString())))
                    .andExpect(jsonPath("$.user.email", is("buyer@example.test")))
                    .andExpect(jsonPath("$.user.role", is("BUYER")))
                    .andExpect(jsonPath("$.user.fullName", is("Anna Ivanova")))
                    .andExpect(jsonPath("$.user.phone", is("+79990000001")));
        }

        @Test
        @DisplayName("422: ошибка валидации если пароль короче 8 символов или email невалиден")
        void shouldReturn422WhenFieldsAreInvalid() throws Exception {
            String requestJson = """
                    {
                        "email": "not-an-email",
                        "password": "short"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/register/buyer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().is(422))
                    .andExpect(jsonPath("$.type", is("ValidationError")))
                    .andExpect(jsonPath("$.status", is(422)))
                    .andExpect(jsonPath("$.details.email", is("Invalid email format")))
                    .andExpect(jsonPath("$.details.password", containsString("8")));
        }

        @Test
        @DisplayName("409: конфликт если email уже занят")
        void shouldReturn409WhenEmailAlreadyExists() throws Exception {
            when(authenticationService.registerBuyer(any(RegisterBuyerCommand.class)))
                    .thenThrow(new EmailAlreadyUsedException("Пользователь с таким email уже зарегистрирован"));

            String requestJson = """
                    {
                        "email": "existing@example.test",
                        "password": "password123"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/register/buyer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status", is(409)))
                    .andExpect(jsonPath("$.detail", is("User with this email is already registered")));
        }

        @Test
        @DisplayName("400: некорректный JSON синтаксис")
        void shouldReturn400WhenJsonIsMalformed() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register/buyer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ not valid json }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.detail", is("Failed to read request JSON")));
        }
    }

    @Nested
    @DisplayName("2.1 POST /api/v1/auth/register/seller")
    class RegisterSellerTests {

        @Test
        @DisplayName("201: успешная регистрация продавца")
        void shouldRegisterSellerSuccessfully() throws Exception {
            UUID userId = UUID.randomUUID();
            UserProfile profile = createSellerProfile(userId, "seller@example.test");
            AuthSession session = new AuthSession("seller.access.jwt", "seller.refresh.jwt", profile);

            when(authenticationService.registerSeller(any(RegisterSellerCommand.class))).thenReturn(session);

            String requestJson = """
                    {
                        "email": "seller@example.test",
                        "password": "seller-password-123",
                        "companyName": "Romashka LLC",
                        "inn": "7701234560",
                        "description": "Flower shop"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/register/seller")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=seller.refresh.jwt")))
                    .andExpect(jsonPath("$.accessToken", is("seller.access.jwt")))
                    .andExpect(jsonPath("$.user.role", is("SELLER")))
                    .andExpect(jsonPath("$.user.companyName", is("Romashka LLC")))
                    .andExpect(jsonPath("$.user.inn", is("7701234560")));
        }

        @Test
        @DisplayName("422: ИНН неверного формата (не 10 и не 12 цифр)")
        void shouldReturn422WhenInnIsInvalid() throws Exception {
            String requestJson = """
                    {
                        "email": "seller@example.test",
                        "password": "seller-password-123",
                        "companyName": "Romashka LLC",
                        "inn": "12345"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/register/seller")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().is(422))
                    .andExpect(jsonPath("$.type", is("ValidationError")))
                    .andExpect(jsonPath("$.status", is(422)))
                    .andExpect(jsonPath("$.details.inn", is("INN must consist of 10 or 12 digits")));
        }
    }

    @Nested
    @DisplayName("2.1 POST /api/v1/auth/login")
    class LoginTests {

        @Test
        @DisplayName("200: успешный вход возвращает сессию и cookie")
        void shouldLoginSuccessfully() throws Exception {
            UUID userId = UUID.randomUUID();
            UserProfile profile = createBuyerProfile(userId, "buyer@example.test");
            AuthSession session = new AuthSession("login.access.jwt", "login.refresh.jwt", profile);

            when(authenticationService.signIn(any(SignInCommand.class))).thenReturn(session);

            String requestJson = """
                    {
                        "email": "buyer@example.test",
                        "password": "password123"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=login.refresh.jwt")))
                    .andExpect(jsonPath("$.accessToken", is("login.access.jwt")))
                    .andExpect(jsonPath("$.user.email", is("buyer@example.test")));
        }

        @Test
        @DisplayName("401: неверный email или пароль")
        void shouldReturn401WhenCredentialsAreInvalid() throws Exception {
            when(authenticationService.signIn(any(SignInCommand.class)))
                    .thenThrow(new InvalidCredentialsException("Неверный email или пароль"));

            String requestJson = """
                    {
                        "email": "wrong@example.test",
                        "password": "wrong-password"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.detail", is("Invalid email or password")));
        }
    }

    @Nested
    @DisplayName("2.1 POST /api/v1/auth/refresh")
    class RefreshTests {

        @Test
        @DisplayName("200: обновление сессии по refresh cookie")
        void shouldRefreshSessionSuccessfully() throws Exception {
            UUID userId = UUID.randomUUID();
            UserProfile profile = createBuyerProfile(userId, "buyer@example.test");
            AuthSession session = new AuthSession("new.access.jwt", "new.refresh.jwt", profile);

            when(authenticationService.refresh(any(RefreshSessionCommand.class))).thenReturn(session);

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie("refreshToken", "valid.old.refresh.jwt")))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=new.refresh.jwt")))
                    .andExpect(jsonPath("$.accessToken", is("new.access.jwt")));
        }

        @Test
        @DisplayName("401: cookie отсутствует")
        void shouldReturn401WhenRefreshCookieMissing() throws Exception {
            mockMvc.perform(post("/api/v1/auth/refresh"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.detail", is("Session is invalid or expired")));
        }

        @Test
        @DisplayName("401: недействительный или истёкший токен")
        void shouldReturn401WhenRefreshTokenIsInvalid() throws Exception {
            when(authenticationService.refresh(any(RefreshSessionCommand.class)))
                    .thenThrow(new InvalidTokenException("Сессия недействительна или истекла"));

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie("refreshToken", "expired.jwt")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.detail", is("Session is invalid or expired")));
        }
    }

    @Nested
    @DisplayName("2.1 POST /api/v1/auth/logout")
    class LogoutTests {

        @Test
        @DisplayName("204: успешный выход сбрасывает cookie (Max-Age=0)")
        void shouldLogoutSuccessfully() throws Exception {
            doNothing().when(authenticationService).logout(any(LogoutCommand.class));

            mockMvc.perform(post("/api/v1/auth/logout")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer valid.access.jwt"))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

            verify(authenticationService).logout(any(LogoutCommand.class));
        }

        @Test
        @DisplayName("401: заголовок Authorization отсутствует")
        void shouldReturn401WhenAuthorizationHeaderMissing() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.detail", is("Active access token required")));
        }

        @Test
        @DisplayName("401: access токен недействителен")
        void shouldReturn401WhenAccessTokenIsInvalid() throws Exception {
            doThrow(new InvalidTokenException("Требуется действующий access-токен"))
                    .when(authenticationService).logout(any(LogoutCommand.class));

            mockMvc.perform(post("/api/v1/auth/logout")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.access.jwt"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.detail", is("Active access token required")));
        }
    }
}
