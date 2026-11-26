package auth.web

import auth.AuthApplication
import auth.domain.model.UserModel
import auth.domain.repository.UserModelRepository
import auth.infrastructure.persistence.repository.DatabaseUnitTestConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID

/*
* Проверяет публичные данные магазина и отсутствие приватных полей аккаунта
* */
@SpringBootTest(classes = [AuthApplication::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SellerApiTest : DatabaseUnitTestConfiguration() {
    @Autowired
    private lateinit var users: UserModelRepository

    @LocalServerPort
    private var port: Int = 0

    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()

    @Test
    fun returnsShopWithoutJwtAndWithoutPrivateFields() {
        val id = UUID.randomUUID()
        users.save(
            UserModel.registerSeller(id, "$id@example.com", "private-hash", "Книжный магазин", id.toString().filter { it.isDigit() }.take(10).padEnd(10, '0'), "Описание", Instant.now()),
        )
        val response = get(id)
        assertEquals(200, response.statusCode(), response.body())
        val body = json.readTree(response.body())
        assertEquals(id.toString(), body["sellerId"].stringValue())
        assertEquals("Книжный магазин", body["companyName"].stringValue())
        assertEquals("Описание", body["description"].stringValue())
        assertEquals(3, body.size())
        for (field in listOf("email", "passwordHash", "inn", "role", "phone")) {
            assertFalse(body.has(field))
        }
    }

    @Test
    fun doesNotExposeBuyerAsSellerAndReturns404ForMissingAccount() {
        val id = UUID.randomUUID()
        users.save(UserModel.registerBuyer(id, "$id@example.com", "private-hash", "Buyer", null, Instant.now()))
        assertEquals(404, get(id).statusCode())
        assertEquals(404, get(UUID.randomUUID()).statusCode())
    }

    private fun get(id: UUID): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/sellers/$id")).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
    )
}
