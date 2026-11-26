package catalog.web

import catalog.CatalogApplication
import catalog.common.enums.ProductStatus
import catalog.infrastructure.search.ElasticsearchUnitTestConfiguration
import catalog.infrastructure.search.ProductDocument
import catalog.infrastructure.search.ProductIndexInitializer
import org.springframework.beans.factory.annotation.Autowired
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/*
* Проверяет HTTP API каталога с PostgreSQL Elasticsearch и HTTP интеграцией магазинов
* */
@SpringBootTest(classes = [CatalogApplication::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicCatalogApiTest : ElasticsearchUnitTestConfiguration() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var productEntities: catalog.infrastructure.persistence.repository.ProductRepository

    @Autowired
    private lateinit var categoryRepository: catalog.domain.repository.CategoryRepository

    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()
    private val books = id(1)
    private val auto = id(2)
    private val closed = id(3)
    private val child = id(4)
    private val sellerA = id(10)
    private val sellerB = id(11)

    @BeforeEach
    fun seed() {
        sellerStatus.set(200)
        sellerRequests.set(0)
        jdbcTemplate.update(
            "INSERT INTO catalog.categories(id, name, slug, is_active) VALUES (?, 'Books', 'books', true), (?, 'Auto', 'auto', true), (?, 'Closed', 'closed', false)",
            books, auto, closed,
        )
        jdbcTemplate.update(
            "INSERT INTO catalog.categories(id, parent_id, name, slug) VALUES (?, ?, 'Child', 'child')",
            child, closed,
        )
        val docs = listOf(
            product(101, "Новая книга", 10, books, "books", sellerA),
            product(102, "Редкая книга", 50, books, "books", sellerB),
            product(103, "Колесо", 20, auto, "auto", sellerA),
            product(104, "Скрытая книга", 5, books, "books", sellerA, ProductStatus.HIDDEN),
            product(105, "Архивная книга", 5, books, "books", sellerA, ProductStatus.ARCHIVED),
            product(106, "Секретная книга", 5, child, "child", sellerA, categoryActive = false),
        )
        indexRepository.saveAll(docs)
        jdbcTemplate.update(
            "INSERT INTO catalog.product_images(product_id, media_id, sort_order) VALUES (?, ?, 2), (?, ?, 0)",
            id(101), id(202), id(101), id(201),
        )
    }

    @Test
    fun listsPublicProductsAndSellerDetailsWithoutJwt() {
        val body = getProducts("sort" to "price,asc")
        assertEquals(3, body["total"].asInt())
        assertEquals(listOf(10L, 20L, 50L), body["items"].toList().map { it["price"].asLong() })
        val first = body["items"][0]
        assertEquals("Магазин книг", first["companyName"].stringValue())
        assertEquals("Описание магазина", first["sellerDescription"].stringValue())
        assertEquals(listOf(id(201).toString(), id(202).toString()), first["images"].toList().map { it["mediaId"].stringValue() })
        assertFalse(first.has("passwordHash"))
        assertFalse(first.has("email"))
        assertEquals(2, sellerRequests.get())
    }

    @Test
    fun searchesFiltersSortsAndReturnsSecondPage() {
        val body = getProducts(
            "q" to "книги",
            "f[category]" to "books,auto",
            "f[price]" to "10,50",
            "sort" to "price,asc",
            "page" to "1",
            "size" to "1",
        )
        assertEquals(2, body["total"].asInt())
        assertEquals(1, body["page"].asInt())
        assertEquals(1, body["size"].asInt())
        assertEquals(id(102).toString(), body["items"][0]["id"].stringValue())
        val excluded = getProducts("f[sellerId]!" to sellerA.toString())
        assertEquals(id(102).toString(), excluded["items"][0]["id"].stringValue())
        assertEquals(1, excluded["total"].asInt())
        val byCategoryId = getProducts("f[categoryId]" to auto.toString())
        assertEquals(id(103).toString(), byCategoryId["items"][0]["id"].stringValue())
        val typo = getProducts("q" to "кнгиа")
        assertEquals(2, typo["total"].asInt())
    }

    @Test
    fun returnsEmptyPageAndKeepsTotal() {
        val body = getProducts("page" to "9", "size" to "20")
        assertEquals(3, body["total"].asInt())
        assertEquals(0, body["items"].size())
        val noMatch = getProducts("q" to "самолет")
        assertEquals(0, noMatch["total"].asInt())
        assertEquals(0, noMatch["items"].size())
    }

    @Test
    fun initializesSearchIndexFromPostgres() {
        elasticsearchOperations.indexOps(ProductDocument::class.java).delete()
        ProductIndexInitializer(productEntities, categoryRepository, indexRepository).initialize()
        val body = getProducts("sort" to "price,asc")
        assertEquals(3, body["total"].asInt())
        assertEquals(listOf(10L, 20L, 50L), body["items"].toList().map { it["price"].asLong() })
    }

    @Test
    fun rejectsInvalidAndRepeatedParametersWithProblemDetails() {
        val invalid = listOf(
            listOf("page" to "-1"),
            listOf("page" to "abc"),
            listOf("size" to "0"),
            listOf("size" to "101"),
            listOf("page" to "2147483647", "size" to "100"),
            listOf("f[price]" to "50,10"),
            listOf("f[price]" to "10"),
            listOf("f[unknown]" to "x"),
            listOf("f[sellerId]" to "invalid"),
            listOf("f[categoryId]" to "invalid"),
            listOf("f[price]!" to "10,20"),
            listOf("sort" to "rating,desc"),
            listOf("sort" to "price,up"),
            listOf("page" to "0", "page" to "1"),
            listOf("f[category]" to "books", "f[category]" to "auto"),
            listOf("cursor" to "unsupported"),
            listOf("f[category" to "books"),
        )
        for (parameters in invalid) {
            val response = request("/products", parameters)
            assertEquals(400, response.statusCode(), parameters.toString())
            assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("application/problem+json"))
            val body = json.readTree(response.body())
            assertEquals(400, body["status"].asInt())
            assertEquals("/api/v1/catalog/products", body["instance"].stringValue())
            assertTrue(body.has("timestamp"))
        }
    }

    @Test
    fun hidesUnavailableProductsAndCategories() {
        for (number in listOf(104, 105, 106, 999)) {
            assertEquals(404, request("/products/${id(number)}").statusCode())
        }
        assertEquals(400, request("/products/not-a-uuid").statusCode())
        val categories = json.readTree(request("/categories").body())
        assertEquals(setOf("books", "auto"), categories.toList().map { it["slug"].stringValue() }.toSet())
        assertEquals(404, request("/categories", listOf("parentId" to closed.toString())).statusCode())
        assertEquals(400, request("/categories", listOf("tree" to "yes")).statusCode())
        val detail = json.readTree(request("/products/${id(101)}").body())
        assertEquals("Магазин книг", detail["companyName"].stringValue())
        jdbcTemplate.update("UPDATE catalog.products SET status = 'HIDDEN' WHERE id = ?", id(101))
        val staleIndex = getProducts("f[category]" to "books")
        assertFalse(staleIndex["items"].any { it["id"].stringValue() == id(101).toString() })
    }

    @Test
    fun reportsSellerDependencyFailureAsServiceUnavailable() {
        sellerStatus.set(503)
        val response = request("/products")
        assertEquals(503, response.statusCode())
        val body = json.readTree(response.body())
        assertEquals(503, body["status"].asInt())
        assertFalse(response.body().contains("upstream failure"))
    }

    @Test
    fun documentsImplementedRoutesParametersAndSchemas() {
        val response = documentationRequest("/v3/api-docs")
        assertEquals(200, response.statusCode(), response.body())
        val document = json.readTree(response.body())
        assertTrue(document["openapi"].stringValue().startsWith("3.1."))
        val paths = document["paths"]
        assertEquals(
            setOf("/api/v1/catalog/products", "/api/v1/catalog/products/{id}", "/api/v1/catalog/categories"),
            paths.propertyNames().toSet(),
        )
        val products = paths["/api/v1/catalog/products"]["get"]
        assertEquals("listCatalogProducts", products["operationId"].stringValue())
        val parameters = products["parameters"].toList().associateBy { it["name"].stringValue() }
        assertEquals(
            setOf("q", "f[price]", "f[category]", "f[categoryId]", "f[sellerId]", "f[category]!", "f[categoryId]!", "f[sellerId]!", "sort", "page", "size"),
            parameters.keys,
        )
        assertEquals(100, parameters.getValue("size")["schema"]["maximum"].asInt())
        assertEquals(20, parameters.getValue("size")["schema"]["default"].asInt())
        assertEquals(0, parameters.getValue("page")["schema"]["default"].asInt())
        assertFalse(parameters.getValue("sort")["schema"].has("default"))
        assertEquals(setOf("200", "400", "503"), products["responses"].propertyNames().toSet())
        for (path in paths.propertyNames()) {
            val operation = paths[path]["get"]
            assertTrue(operation["security"] == null || operation["security"].isEmpty)
            assertTrue(operation["responses"]["400"]["content"].has("application/problem+json"))
        }
        val schemas = document["components"]["schemas"]
        val product = schemas["ProductResponse"]
        assertEquals(product["properties"].propertyNames().toSet(), product["required"].toList().map { it.stringValue() }.toSet())
        for (field in listOf("description", "companyName", "sellerDescription")) {
            assertTrue(product["properties"][field]["type"].toList().any { it.stringValue() == "null" }, field)
        }
        assertTrue(schemas["CategoryResponse"]["properties"]["parentId"]["type"].toList().any { it.stringValue() == "null" })
        assertEquals("date-time", product["properties"]["createdAt"]["format"].stringValue())
        assertEquals("uuid", product["properties"]["id"]["format"].stringValue())
    }

    @Test
    fun servesSwaggerUiAndYamlContract() {
        assertEquals(200, documentationRequest("/swagger-ui/index.html").statusCode())
        val config = documentationRequest("/v3/api-docs/swagger-config")
        assertEquals(200, config.statusCode())
        assertEquals("/v3/api-docs", json.readTree(config.body())["url"].stringValue())
        val yamlResponse = documentationRequest("/v3/api-docs.yaml")
        assertEquals(200, yamlResponse.statusCode())
        val yamlMapper = tools.jackson.dataformat.yaml.YAMLMapper.builder().build()
        val generatedYaml = yamlMapper.readTree(yamlResponse.body())
        assertEquals(json.readTree(documentationRequest("/v3/api-docs").body()), generatedYaml)
        val snapshotResponse = documentationRequest("/openapi/catalog.yaml")
        assertEquals(200, snapshotResponse.statusCode())
        val snapshot = yamlMapper.readTree(snapshotResponse.body())
        assertEquals(generatedYaml, snapshot)
    }

    private fun documentationRequest(path: String): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI("http://localhost:$port$path")).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun getProducts(vararg parameters: Pair<String, String>): JsonNode {
        val response = request("/products", parameters.toList())
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    private fun request(path: String, parameters: List<Pair<String, String>> = emptyList()): HttpResponse<String> {
        val query = parameters.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, StandardCharsets.UTF_8)}=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
        }
        val url = "http://localhost:$port/api/v1/catalog$path" + if (query.isEmpty()) "" else "?$query"
        return client.send(HttpRequest.newBuilder(URI(url)).GET().build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun product(
        number: Int,
        title: String,
        price: Long,
        categoryId: UUID,
        slug: String,
        sellerId: UUID,
        status: ProductStatus = ProductStatus.ACTIVE,
        categoryActive: Boolean = true,
    ): ProductDocument {
        val created = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(number.toLong())
        jdbcTemplate.update(
            "INSERT INTO catalog.products(id, seller_id, category_id, title, price, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            id(number), sellerId, categoryId, title, price, status.name, java.sql.Timestamp.from(created),
        )
        return ProductDocument(
            id = id(number).toString(),
            sellerId = sellerId.toString(),
            categoryId = categoryId.toString(),
            categorySlug = slug,
            categoryActive = categoryActive,
            status = status.name,
            title = title,
            price = price,
            createdAt = created,
        )
    }

    private fun id(number: Int): UUID = UUID.fromString("00000000-0000-0000-0000-${number.toString().padStart(12, '0')}")

    companion object {
        private val sellerStatus = AtomicInteger(200)
        private val sellerRequests = AtomicInteger(0)
        private val sellerServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/v1/sellers/") { exchange ->
                sellerRequests.incrementAndGet()
                val sellerId = exchange.requestURI.path.removeSuffix("/shop").substringAfterLast('/')
                val status = sellerStatus.get()
                val body = if (status == 200) {
                    """{"sellerId":"$sellerId","companyName":"Магазин книг","description":"Описание магазина"}"""
                } else {
                    """{"detail":"upstream failure"}"""
                }
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureAuth(registry: DynamicPropertyRegistry) {
            registry.add("catalog.auth.base-url") { "http://127.0.0.1:${sellerServer.address.port}" }
        }
    }
}
