package catalog.integration

import catalog.CatalogApplication
import catalog.domain.repository.CategoryRepository as DomainCategories
import catalog.domain.repository.ProductRepository as DomainProducts
import com.jayway.jsonpath.JsonPath
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import catalog.infrastructure.search.ProductDocument
import catalog.infrastructure.search.ProductIndexInitializer
import catalog.infrastructure.search.ElasticsearchProductIndexRepository
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

/**
 * Проверяет весь Catalog с PostgreSQL.
 */
@SpringBootTest(
    classes = [CatalogApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
class CatalogLayerIntegrationTest {

    @Value("\${local.server.port}")
    private var port: Int = 0

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var categories: DomainCategories

    @Autowired
    private lateinit var products: DomainProducts

    @Autowired
    private lateinit var productEntities: catalog.infrastructure.persistence.repository.ProductRepository

    @Autowired
    private lateinit var index: ElasticsearchProductIndexRepository

    @Autowired
    private lateinit var elasticsearchOperations: ElasticsearchOperations

    private val categoryId = UUID.randomUUID()
    private val productId = UUID.randomUUID()
    private val mediaId = UUID.randomUUID()

    // Заполняет реальные таблицы каталога.
    @BeforeEach
    fun seedDatabase() {
        authStatus = 200
        jdbcTemplate.execute("TRUNCATE TABLE catalog.product_images, catalog.products, catalog.categories CASCADE")
        jdbcTemplate.update(
            "INSERT INTO catalog.categories(id, name, slug) VALUES (?, ?, ?)",
            categoryId, "Электроника", "electronics"
        )
        jdbcTemplate.update(
            """INSERT INTO catalog.products(id, seller_id, category_id, title, price, status)
               VALUES (?, ?, ?, ?, ?, ?)""",
            productId, SELLER_ID, categoryId, "Телефон", 1500L, "ACTIVE"
        )
        jdbcTemplate.update(
            "INSERT INTO catalog.product_images(product_id, media_id, sort_order) VALUES (?, ?, ?)",
            productId, mediaId, 0
        )
        val indexOps = elasticsearchOperations.indexOps(ProductDocument::class.java)
        if (indexOps.exists()) indexOps.delete()
        ProductIndexInitializer(productEntities, categories, index).initialize()
    }

    // Проверяет адаптеры после завершения транзакции.
    @Test
    fun mapsDatabaseEntitiesAndLazyImages() {
        assertEquals("Электроника", categories.findById(categoryId)!!.name)
        assertNull(categories.findById(UUID.randomUUID()))
        val product = products.findById(productId)!!
        assertEquals(mediaId, product.images.single().mediaId)
        assertNotNull(product.createdAt)
        assertEquals(1, products.findAll().size)
        assertNull(products.findById(UUID.randomUUID()))
    }

    // Проверяет карточку через настоящий HTTP.
    @Test
    fun servesProductFromDatabaseWithAuthShop() {
        val response = get("/api/v1/catalog/products/" + productId)
        assertEquals(200, response.statusCode())
        assertEquals("Телефон", JsonPath.read<String>(response.body(), "$.title"))
        assertEquals("Магазин из Auth", JsonPath.read<String>(response.body(), "$.companyName"))
        assertEquals(mediaId.toString(), JsonPath.read<String>(response.body(), "$.images[0].mediaId"))
    }

    // Проверяет категории через настоящий HTTP.
    @Test
    fun servesCategoriesFromDatabase() {
        val response = get("/api/v1/catalog/categories")
        assertEquals(200, response.statusCode())
        assertEquals(categoryId.toString(), JsonPath.read<String>(response.body(), "$[0].id"))
    }

    // Проверяет отказ скрытому товару.
    @Test
    fun hidesUnavailableProducts() {
        jdbcTemplate.update("UPDATE catalog.products SET status = 'HIDDEN' WHERE id = ?", productId)
        assertEquals(404, get("/api/v1/catalog/products/" + productId).statusCode())
        assertEquals(0, JsonPath.read<List<Any>>(get("/api/v1/catalog/products").body(), "$.items").size)
    }

    // Проверяет отсутствие магазина без сбоя.
    @Test
    fun keepsProductWhenShopIsMissing() {
        authStatus = 404
        val response = get("/api/v1/catalog/products/" + productId)
        assertEquals(200, response.statusCode())
        assertNull(JsonPath.read<String?>(response.body(), "$.companyName"))
    }

    // Проверяет HTTP-ошибку недоступного Auth.
    @Test
    fun returns503WhenAuthFails() {
        authStatus = 500
        val response = get("/api/v1/catalog/products/" + productId)
        assertEquals(503, response.statusCode())
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/problem+json"))
        assertEquals(503, JsonPath.read<Int>(response.body(), "$.status"))
    }

    // Выполняет запрос запущенному Catalog.
    private fun get(path: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:" + port + path)).GET().build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    }

    companion object {
        private val elasticsearch = GenericContainer(DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.0.0"))
            .withExposedPorts(9200)
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m")
            .waitingFor(Wait.forHttp("/_cluster/health").forStatusCode(200))
            .also { it.start() }

        private val SELLER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003")
        // Проверяем миграции приложения на пустой БД вместо ручного Flyway.migrate().
        private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("marketplace")
            .withUsername("postgres")
            .withPassword("postgres")
            .also { it.start() }
        @Volatile
        private var authStatus = 200
        private val authServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/v1/sellers/" + SELLER_ID + "/shop") { exchange ->
                val body = if (authStatus == 200) {
                    """{"sellerId":"$SELLER_ID","companyName":"Магазин из Auth","description":null}"""
                } else {
                    "{}"
                }.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(authStatus, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
            Runtime.getRuntime().addShutdownHook(Thread { stop(0) })
        }

        // Подключает контролируемый HTTP-сервис Auth.
        @JvmStatic
        @DynamicPropertySource
        fun authProperties(registry: DynamicPropertyRegistry) {
            registry.add("catalog.search.initialize-on-startup") { "false" }
            registry.add("spring.elasticsearch.uris") { "http://${elasticsearch.host}:${elasticsearch.getMappedPort(9200)}" }
            registry.add("spring.datasource.url") {
                val url = postgres.jdbcUrl
                url + (if (url.contains("?")) "&" else "?") + "currentSchema=catalog,public"
            }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("catalog.auth.base-url") {
                "http://127.0.0.1:" + authServer.address.port
            }
        }
    }
}
