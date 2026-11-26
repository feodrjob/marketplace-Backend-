package catalog.DAO

import catalog.DAO.repositoryImpl.SellerShopLookupImpl
import catalog.exception.SellerShopUnavailableException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.*
import org.springframework.web.client.RestClient
import java.net.SocketTimeoutException
import java.util.UUID

/**
 * Проверяет HTTP-контракт адаптера магазина.
 */
class SellerShopLookupImplTest {

    private lateinit var server: MockRestServiceServer
    private lateinit var lookup: SellerShopLookupImpl
    private val sellerId = UUID.randomUUID()

    // Подменяет HTTP-ответы сервиса Auth.
    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl("http://auth.test")
        server = MockRestServiceServer.bindTo(builder).build()
        lookup = SellerShopLookupImpl(builder.build())
    }

    // Проверяет выполнение ожидаемых запросов.
    @AfterEach
    fun verifyRequests() {
        server.verify()
    }

    // Проверяет преобразование публичного магазина.
    @Test
    fun readsShopWithNullableDescription() {
        server.expect(requestTo("http://auth.test/api/v1/sellers/" + sellerId + "/shop"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                """{"sellerId":"$sellerId","companyName":"Магазин","description":null}""",
                MediaType.APPLICATION_JSON
            ))

        val shop = lookup.findBySellerId(sellerId)
        assertNotNull(shop)
        assertEquals(sellerId, shop!!.sellerId)
        assertEquals("Магазин", shop.companyName)
        assertNull(shop.description)
    }

    // Проверяет отсутствие магазина при 404.
    @Test
    fun returnsNullOnlyFor404() {
        server.expect(requestTo("http://auth.test/api/v1/sellers/" + sellerId + "/shop"))
            .andRespond(withResourceNotFound())
        assertNull(lookup.findBySellerId(sellerId))
    }

    // Проверяет отказ сервиса Auth.
    @Test
    fun reportsServerFailure() {
        server.expect(requestTo("http://auth.test/api/v1/sellers/" + sellerId + "/shop"))
            .andRespond(withServerError())
        assertThrows(SellerShopUnavailableException::class.java) { lookup.findBySellerId(sellerId) }
    }

    // Проверяет ограничение ожидания ответа.
    @Test
    fun reportsTimeout() {
        server.expect(requestTo("http://auth.test/api/v1/sellers/" + sellerId + "/shop"))
            .andRespond(withException(SocketTimeoutException("Auth timeout")))
        assertThrows(SellerShopUnavailableException::class.java) { lookup.findBySellerId(sellerId) }
    }

    // Проверяет некорректный ответ Auth.
    @Test
    fun rejectsMalformedResponse() {
        server.expect(requestTo("http://auth.test/api/v1/sellers/" + sellerId + "/shop"))
            .andRespond(withSuccess("{invalid-json", MediaType.APPLICATION_JSON))
        assertThrows(SellerShopUnavailableException::class.java) { lookup.findBySellerId(sellerId) }
    }

    // Проверяет соответствие продавца запросу.
    @Test
    fun rejectsAnotherSeller() {
        val anotherSeller = UUID.randomUUID()
        server.expect(requestTo("http://auth.test/api/v1/sellers/" + sellerId + "/shop"))
            .andRespond(withSuccess(
                """{"sellerId":"$anotherSeller","companyName":"Магазин","description":null}""",
                MediaType.APPLICATION_JSON
            ))
        assertThrows(SellerShopUnavailableException::class.java) { lookup.findBySellerId(sellerId) }
    }

    // Проверяет пустой ответ Auth.
    @Test
    fun rejectsEmptyResponse() {
        server.expect(requestTo("http://auth.test/api/v1/sellers/" + sellerId + "/shop"))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON))
        assertThrows(SellerShopUnavailableException::class.java) { lookup.findBySellerId(sellerId) }
    }
}
