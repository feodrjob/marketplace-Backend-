package auth.web;

import auth.common.enums.Role;
import auth.domain.model.UpdateProfileCommand;
import auth.domain.model.UserProfile;
import auth.domain.service.UserService;
import auth.exception.InvalidTokenException;
import auth.exception.ProfileUpdateNotAllowedException;
import auth.web.controller.UserController;
import auth.web.exception.GlobalExceptionHandler;
import auth.web.mapper.AuthDtoMapper;
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

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    private final AuthDtoMapper mapper = new AuthDtoMapper();

    @BeforeEach
    void setUp() {
        UserController controller = new UserController(userService, mapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private UserProfile createBuyerProfile(UUID id) {
        return new UserProfile(
                id,
                "buyer@example.test",
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

    @Nested
    @DisplayName("2.2 GET /api/v1/users/me")
    class GetCurrentUserTests {

        @Test
        @DisplayName("200: получение своего профиля по валидному Bearer токену")
        void shouldReturnCurrentUserProfile() throws Exception {
            UUID userId = UUID.randomUUID();
            UserProfile profile = createBuyerProfile(userId);

            when(userService.getProfile(eq("valid.access.jwt"))).thenReturn(profile);

            mockMvc.perform(get("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer valid.access.jwt"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(userId.toString())))
                    .andExpect(jsonPath("$.email", is("buyer@example.test")))
                    .andExpect(jsonPath("$.role", is("BUYER")))
                    .andExpect(jsonPath("$.fullName", is("Anna Ivanova")))
                    .andExpect(jsonPath("$.phone", is("+79990000001")));
        }

        @Test
        @DisplayName("401: отсутствует заголовок Authorization")
        void shouldReturn401WhenAuthorizationHeaderMissing() throws Exception {
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.detail", is("Active access token required")));
        }

        @Test
        @DisplayName("401: недействительный access токен")
        void shouldReturn401WhenTokenIsInvalid() throws Exception {
            when(userService.getProfile(eq("invalid.access.jwt")))
                    .thenThrow(new InvalidTokenException("Сессия недействительна или истекла"));

            mockMvc.perform(get("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.access.jwt"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.detail", is("Session is invalid or expired")));
        }
    }

    @Nested
    @DisplayName("2.2 PATCH /api/v1/users/me")
    class UpdateCurrentUserTests {

        @Test
        @DisplayName("200: успешное обновление полей профиля покупателя")
        void shouldUpdateBuyerProfileSuccessfully() throws Exception {
            UUID userId = UUID.randomUUID();
            UserProfile updated = new UserProfile(
                    userId,
                    "buyer@example.test",
                    Role.BUYER,
                    "Anna Petrova",
                    "+79998887766",
                    null,
                    null,
                    null,
                    null,
                    Instant.parse("2026-11-13T12:00:00Z"),
                    Instant.parse("2026-11-13T12:10:00Z")
            );

            when(userService.updateProfile(any(UpdateProfileCommand.class))).thenReturn(updated);

            String requestJson = """
                    {
                        "fullName": "Anna Petrova",
                        "phone": "+79998887766"
                    }
                    """;

            mockMvc.perform(patch("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer valid.access.jwt")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fullName", is("Anna Petrova")))
                    .andExpect(jsonPath("$.phone", is("+79998887766")));
        }

        @Test
        @DisplayName("422: попытка изменить ИНН вызывает ошибку 422")
        void shouldReturn422WhenAttemptingToChangeInn() throws Exception {
            when(userService.updateProfile(any(UpdateProfileCommand.class)))
                    .thenThrow(new ProfileUpdateNotAllowedException("ИНН нельзя изменить после регистрации"));

            String requestJson = """
                    {
                        "inn": "7701999999"
                    }
                    """;

            mockMvc.perform(patch("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer valid.access.jwt")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().is(422))
                    .andExpect(jsonPath("$.type", is("ValidationError")))
                    .andExpect(jsonPath("$.status", is(422)))
                    .andExpect(jsonPath("$.details.inn", is("INN cannot be changed after registration")));
        }

        @Test
        @DisplayName("401: отсутствует заголовок Authorization при PATCH")
        void shouldReturn401WhenAuthHeaderMissingOnPatch() throws Exception {
            mockMvc.perform(patch("/api/v1/users/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.detail", is("Active access token required")));
        }
    }
}
