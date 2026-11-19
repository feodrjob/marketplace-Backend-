package auth.web

import auth.config.SecurityConfig
import auth.domain.model.UserModel
import auth.domain.service.TokenService
import auth.domain.service.UserService
import auth.domain.support.AuthFixture
import auth.web.controller.SellerShopController
import auth.web.exception.GlobalExceptionHandler
import auth.web.mapper.AuthDtoMapper
import auth.web.security.JwtAuthenticationFilter
import auth.web.security.RestAuthenticationEntryPoint
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.Filter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import java.util.UUID

/**
 * Проверяет публичный магазин вместе с Security.
 */
@SpringJUnitWebConfig(SellerShopControllerTest.TestConfig::class)
class SellerShopControllerTest {

    @Autowired
    private lateinit var context: WebApplicationContext
    private lateinit var mockMvc: MockMvc

    // Подключает настоящую цепочку безопасности.
    @BeforeEach
    fun setUp() {
        val security = context.getBean("springSecurityFilterChain", Filter::class.java)
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters<DefaultMockMvcBuilder>(security)
            .build()
    }

    // Проверяет публичность и состав ответа.
    @Test
    fun returnsOnlyPublicShopFieldsWithoutJwt() {
        mockMvc.perform(get("/api/v1/sellers/{sellerId}/shop", SELLER_ID))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$.sellerId").value(SELLER_ID.toString()))
            .andExpect(jsonPath("$.companyName").value("Тестовый магазин"))
            .andExpect(jsonPath("$.description").value("Описание магазина"))
            .andExpect(jsonPath("$.email").doesNotExist())
            .andExpect(jsonPath("$.inn").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
    }

    // Проверяет отсутствие неизвестного продавца.
    @Test
    fun returns404ForUnknownSeller() {
        mockMvc.perform(get("/api/v1/sellers/{sellerId}/shop", UUID.randomUUID()))
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
    }

    // Проверяет запрет магазина покупателя.
    @Test
    fun returns404ForBuyer() {
        mockMvc.perform(get("/api/v1/sellers/{sellerId}/shop", BUYER_ID))
            .andExpect(status().isNotFound)
    }

    // Проверяет формат идентификатора продавца.
    @Test
    fun returns400ForInvalidSellerId() {
        mockMvc.perform(get("/api/v1/sellers/invalid/shop"))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
    }

    // Сохраняет защиту личного профиля.
    @Test
    fun keepsPrivateProfileProtected() {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized)
    }

    // Разрешает публично только чтение.
    @Test
    fun keepsShopMutationProtected() {
        mockMvc.perform(post("/api/v1/sellers/{sellerId}/shop", SELLER_ID))
            .andExpect(status().isUnauthorized)
    }

    /**
     * Собирает HTTP-контекст без базы данных.
     */
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(SecurityConfig::class)
    class TestConfig {

        // Подготавливает продавца и покупателя.
        @Bean
        fun fixture(): AuthFixture {
            val fixture = AuthFixture()
            val now = fixture.clock.instant()
            fixture.users.save(UserModel.registerSeller(
                SELLER_ID, "seller@example.test", "test-hash",
                "Тестовый магазин", "7701234560", "Описание магазина", now
            ))
            fixture.users.save(UserModel.registerBuyer(
                BUYER_ID, "buyer@example.test", "test-hash", "Покупатель", null, now
            ))
            return fixture
        }

        // Предоставляет настоящий доменный сервис.
        @Bean
        fun userService(fixture: AuthFixture): UserService = fixture.userService

        // Предоставляет проверку JWT.
        @Bean
        fun tokenService(fixture: AuthFixture): TokenService = fixture.tokens

        // Создаёт маппер HTTP-ответов.
        @Bean
        fun mapper(): AuthDtoMapper = AuthDtoMapper()

        // Создаёт контроллер публичного магазина.
        @Bean
        fun controller(service: UserService, mapper: AuthDtoMapper): SellerShopController =
            SellerShopController(service, mapper)

        // Подключает обработку HTTP-ошибок.
        @Bean
        fun exceptionHandler(): GlobalExceptionHandler = GlobalExceptionHandler()

        // Подключает фильтр JWT.
        @Bean
        fun jwtFilter(tokens: TokenService): JwtAuthenticationFilter = JwtAuthenticationFilter(tokens)

        // Подключает ответ неавторизованному клиенту.
        @Bean
        fun entryPoint(): RestAuthenticationEntryPoint =
            RestAuthenticationEntryPoint(ObjectMapper().findAndRegisterModules())
    }

    companion object {
        private val SELLER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        private val BUYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    }
}
