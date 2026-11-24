package auth.web;

import auth.common.enums.Role;
import auth.infrastructure.persistence.entity.UserEntity;
import auth.infrastructure.persistence.repository.DatabaseUnitTestConfiguration;
import auth.infrastructure.persistence.repository.UserRepository;
import auth.web.security.CookieHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Epic1AuthIntegrationTest extends DatabaseUnitTestConfiguration {

    private static final String PASSWORD = "password12";
    private static final AtomicLong INN_SEQUENCE = new AtomicLong(1_000_000_000L);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilters;

    private MockMvc api;

    @BeforeEach
    void bindApi() {
        api = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilters)
                .build();
    }

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository users;

    @Nested
    @DisplayName("US-1.1. Регистрация покупателя")
    class BuyerRegistration {

        @Test
        @DisplayName("AC 1: успешная регистрация создаёт аккаунт BUYER и не открывает бонусный счёт")
        void createsBuyerAccountWithoutBonusWallet() throws Exception {
            Session session = registerBuyer(email(), PASSWORD, null, null);

            assertEquals("BUYER", session.profile.get("role").asText());
            assertFalse(session.profile.has("balance"));
            assertFalse(session.profile.has("bonusBalance"));
            assertEquals(Role.BUYER, stored(session.email).getRole());
        }

        @Test
        @DisplayName("AC 2: имя и телефон необязательны, но сохраняются, если их передали")
        void nameAndPhoneAreOptional() throws Exception {
            Session minimal = registerBuyer(email(), PASSWORD, null, null);
            assertTrue(minimal.profile.get("fullName").isNull());
            assertTrue(minimal.profile.get("phone").isNull());

            String namedEmail = email();
            Session named = registerBuyer(namedEmail, PASSWORD, "test user", "+79999999999");
            assertEquals("test user", named.profile.get("fullName").asText());
            assertEquals("+79999999999", named.profile.get("phone").asText());
        }

        @Test
        @DisplayName("AC 3: некорректный email получает 422, повторный email получает 409")
        void emailMustBeValidAndUnique() throws Exception {
            api.perform(post("/api/v1/auth/register/buyer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("email", "not-an-email", "password", PASSWORD))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.details.email").value("Invalid email format"));

            String duplicate = email();
            registerBuyer(duplicate, PASSWORD, null, null);
            api.perform(post("/api/v1/auth/register/buyer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("email", duplicate, "password", PASSWORD))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("User with this email is already registered"));
        }

        @Test
        @DisplayName("AC 4: пароль хранится хэшем, в ответе есть access и refresh")
        void passwordIsHashedAndTokenPairIsReturned() throws Exception {
            Session session = registerBuyer(email(), PASSWORD, null, null);

            UserEntity stored = stored(session.email);
            assertNotEquals(PASSWORD, stored.getPasswordHash());
            assertTrue(stored.getPasswordHash().startsWith("$2"));
            assertEquals("ACCESS", claims(session.accessToken).get("token_use").asText());
            assertEquals("REFRESH", claims(session.refreshToken).get("token_use").asText());
        }
    }

    @Nested
    @DisplayName("US-1.2. Регистрация продавца")
    class SellerRegistration {

        @Test
        @DisplayName("AC 1: создаётся отдельный SELLER, аккаунт покупателя не меняется")
        void createsSeparateSellerAndLeavesBuyerUntouched() throws Exception {
            String buyerEmail = email();
            Session buyer = registerBuyer(buyerEmail, PASSWORD, "test buyer", "+79999999999");

            Session seller = registerSeller(email(), PASSWORD, "test seller", inn(10), "test description");

            assertEquals("SELLER", seller.profile.get("role").asText());
            assertNotEquals(buyer.profile.get("id").asText(), seller.profile.get("id").asText());

            api.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(buyer.accessToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("BUYER"))
                    .andExpect(jsonPath("$.email").value(buyerEmail))
                    .andExpect(jsonPath("$.fullName").value("test buyer"));
        }

        @Test
        @DisplayName("AC 2: название и ИНН обязательны, описание можно не передавать")
        void companyNameAndInnAreRequired() throws Exception {
            api.perform(post("/api/v1/auth/register/seller")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("email", email(), "password", PASSWORD, "inn", inn(10)))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.details.companyName").value("Company name is required"));

            api.perform(post("/api/v1/auth/register/seller")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of(
                                    "email", email(),
                                    "password", PASSWORD,
                                    "companyName", "Магазин без ИНН"))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.details.inn").value("INN is required"));

            Session seller = registerSeller(email(), PASSWORD, "Магазин Пример", inn(10), null);
            assertEquals("Магазин Пример", seller.profile.get("companyName").asText());
            assertTrue(seller.profile.get("description").isNull());
        }

        @Test
        @DisplayName("AC 3: на одном email нельзя получить вторую роль")
        void oneEmailKeepsASingleRole() throws Exception {
            String sharedEmail = email();
            registerBuyer(sharedEmail, PASSWORD, null, null);

            api.perform(post("/api/v1/auth/register/seller")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of(
                                    "email", sharedEmail,
                                    "password", PASSWORD,
                                    "companyName", "Вторая роль",
                                    "inn", inn(10)))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("User with this email is already registered"));

            assertEquals(Role.BUYER, stored(sharedEmail).getRole());
        }

        @Test
        @DisplayName("AC 4: ИНН принимает 10 или 12 цифр, другой формат получает 422")
        void innMustBeTenOrTwelveDigits() throws Exception {
            api.perform(post("/api/v1/auth/register/seller")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of(
                                    "email", email(),
                                    "password", PASSWORD,
                                    "companyName", "Неверный ИНН",
                                    "inn", "12345abcde"))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.details.inn").value("INN must consist of 10 or 12 digits"));

            Session legalEntity = registerSeller(email(), PASSWORD, "Юрлицо", inn(10), null);
            assertEquals(10, legalEntity.profile.get("inn").asText().length());

            Session individual = registerSeller(email(), PASSWORD, "ИП", inn(12), null);
            assertEquals(12, individual.profile.get("inn").asText().length());
        }

        @Test
        @DisplayName("AC 5: в access-токене одна роль ROLE_SELLER")
        void accessTokenContainsOnlySellerRole() throws Exception {
            Session seller = registerSeller(email(), PASSWORD, "Магазин Пример", inn(10), null);
            JsonNode token = claims(seller.accessToken);

            assertEquals(Role.SELLER.authority(), token.get("role").asText());
            assertTrue(token.get("role").isTextual());
            assertFalse(token.has("roles"));
        }

        @Test
        @DisplayName("AC 6: после регистрации ИНН нельзя изменить через профиль")
        void innCannotBeChangedAfterRegistration() throws Exception {
            String originalInn = inn(10);
            Session seller = registerSeller(email(), PASSWORD, "Магазин Пример", originalInn, "Описание");

            api.perform(patch("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(seller.accessToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("inn", inn(10)))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.details.inn").value("INN cannot be changed after registration"));

            api.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(seller.accessToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.inn").value(originalInn));
        }
    }

    @Nested
    @DisplayName("US-1.3. Вход и обновление сессии")
    class SignIn {

        @Test
        @DisplayName("AC 1: вход по email и паролю, неверные данные получают 401")
        void loginAcceptsPasswordAndRejectsWrongOne() throws Exception {
            String accountEmail = email();
            registerBuyer(accountEmail, PASSWORD, null, null);

            api.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("email", accountEmail, "password", "wrong-password"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid email or password"));

            api.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("email", accountEmail, "password", PASSWORD))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.email").value(accountEmail))
                    .andExpect(jsonPath("$.accessToken").isNotEmpty());
        }

        @Test
        @DisplayName("AC 2: payload access-токена содержит роль этого аккаунта")
        void accessTokenCarriesAccountRole() throws Exception {
            String buyerEmail = email();
            registerBuyer(buyerEmail, PASSWORD, null, null);
            MvcResult buyerLogin = api.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("email", buyerEmail, "password", PASSWORD))))
                    .andExpect(status().isOk())
                    .andReturn();
            assertEquals(Role.BUYER.authority(), claims(accessToken(buyerLogin)).get("role").asText());

            String sellerEmail = email();
            registerSeller(sellerEmail, PASSWORD, "Магазин Пример", inn(10), null);
            MvcResult sellerLogin = api.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("email", sellerEmail, "password", PASSWORD))))
                    .andExpect(status().isOk())
                    .andReturn();
            assertEquals(Role.SELLER.authority(), claims(accessToken(sellerLogin)).get("role").asText());
        }

        @Test
        @DisplayName("AC 3: refresh-cookie выдаёт новую сессию, без cookie ответ 401")
        void refreshCookieRenewsSession() throws Exception {
            Session registered = registerBuyer(email(), PASSWORD, null, null);

            api.perform(post("/api/v1/auth/refresh"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Session is invalid or expired"));

            MvcResult refreshed = api.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie(CookieHelper.REFRESH_TOKEN_COOKIE_NAME, registered.refreshToken)))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("refreshToken=")))
                    .andReturn();

            String renewedAccess = json.readTree(refreshed.getResponse().getContentAsString()).get("accessToken").asText();
            assertEquals("ACCESS", claims(renewedAccess).get("token_use").asText());
            api.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(renewedAccess)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(registered.email));
        }
    }

    @Nested
    @DisplayName("US-1.4. Редактирование профиля")
    class Profile {

        @Test
        @DisplayName("AC 1: покупатель меняет имя и телефон, баланс бонусов через Auth не меняется")
        void buyerUpdatesNameAndPhoneWithoutBonusBalance() throws Exception {
            Session buyer = registerBuyer(email(), PASSWORD, "Старое имя", "+70000000000");

            api.perform(patch("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(buyer.accessToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("fullName", "Новое имя", "phone", "+79990001122"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fullName").value("Новое имя"))
                    .andExpect(jsonPath("$.phone").value("+79990001122"))
                    .andExpect(jsonPath("$.role").value("BUYER"))
                    .andExpect(jsonPath("$.balance").doesNotExist())
                    .andExpect(jsonPath("$.bonusBalance").doesNotExist());
        }

        @Test
        @DisplayName("AC 2: продавец меняет название и описание, ИНН остаётся прежним")
        void sellerUpdatesShopDetailsButNotInn() throws Exception {
            String originalInn = inn(12);
            Session seller = registerSeller(
                    email(), PASSWORD, "Старый магазин", originalInn, "Старое описание");

            api.perform(patch("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(seller.accessToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("companyName", "Новый магазин", "description", "Новое описание"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.companyName").value("Новый магазин"))
                    .andExpect(jsonPath("$.description").value("Новое описание"))
                    .andExpect(jsonPath("$.inn").value(originalInn));
        }
    }

    @Nested
    @DisplayName("Выход из сессии")
    class Logout {

        @Test
        @DisplayName("Bearer JWT сбрасывает refresh-cookie, без токена ответ 401")
        void logoutClearsRefreshCookie() throws Exception {
            Session buyer = registerBuyer(email(), PASSWORD, null, null);

            api.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isUnauthorized());

            api.perform(post("/api/v1/auth/logout")
                            .header(HttpHeaders.AUTHORIZATION, bearer(buyer.accessToken)))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));
        }
    }

    private Session registerBuyer(String accountEmail, String password, String fullName, String phone) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", accountEmail);
        payload.put("password", password);
        if (fullName != null) {
            payload.put("fullName", fullName);
        }
        if (phone != null) {
            payload.put("phone", phone);
        }
        MvcResult result = api.perform(post("/api/v1/auth/register/buyer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        return session(accountEmail, result);
    }

    private Session registerSeller(
            String accountEmail,
            String password,
            String companyName,
            String inn,
            String description
    ) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", accountEmail);
        payload.put("password", password);
        payload.put("companyName", companyName);
        payload.put("inn", inn);
        if (description != null) {
            payload.put("description", description);
        }
        MvcResult result = api.perform(post("/api/v1/auth/register/seller")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return session(accountEmail, result);
    }

    private Session session(String accountEmail, MvcResult result) throws Exception {
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return new Session(
                accountEmail,
                body.get("accessToken").asText(),
                refreshToken(result),
                body.get("user")
        );
    }

    private UserEntity stored(String accountEmail) {
        return users.findByEmail(accountEmail).orElseThrow();
    }

    private JsonNode claims(String token) throws Exception {
        String encoded = token.split("\\.")[1];
        int remainder = encoded.length() % 4;
        if (remainder > 0) {
            encoded = encoded + "====".substring(remainder);
        }
        return json.readTree(Base64.getUrlDecoder().decode(encoded));
    }

    private static String refreshToken(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String prefix = CookieHelper.REFRESH_TOKEN_COOKIE_NAME + "=";
        int start = header.indexOf(prefix);
        int from = start + prefix.length();
        int end = header.indexOf(';', from);
        return end < 0 ? header.substring(from) : header.substring(from, end);
    }

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private static String accessToken(MvcResult result) throws Exception {
        return new ObjectMapper().readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String body(Map<String, ?> payload) throws Exception {
        return json.writeValueAsString(payload);
    }

    private static String email() {
        return "epic1-" + UUID.randomUUID() + "@example.com";
    }

    private static String inn(int length) {
        String tenDigits = Long.toString(INN_SEQUENCE.getAndIncrement());
        if (length == 10) {
            return tenDigits;
        }
        return "77" + tenDigits;
    }

    private record Session(String email, String accessToken, String refreshToken, JsonNode profile) {
    }
}
