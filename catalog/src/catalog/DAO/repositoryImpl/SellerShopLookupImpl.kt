package catalog.DAO.repositoryImpl

import catalog.domain.model.SellerShop
import catalog.domain.repository.SellerShopLookup
import catalog.exception.SellerShopUnavailableException
import catalog.infrastructure.auth.SellerShopResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.util.UUID

/**
 * Получает публичный магазин через Auth.
 */
@Component
class SellerShopLookupImpl(
    @param:Qualifier("authRestClient") private val client: RestClient,
) : SellerShopLookup {

    // Загружает публичные данные продавца.
    override fun findBySellerId(sellerId: UUID): SellerShop? {
        try {
            val response = client.get()
                .uri("/api/v1/sellers/{sellerId}/shop", sellerId)
                .retrieve()
                .body(SellerShopResponse::class.java)
                ?: throw SellerShopUnavailableException(
                    IllegalStateException("Auth вернул пустой ответ магазина")
                )

            if (response.sellerId != sellerId) {
                throw SellerShopUnavailableException(
                    IllegalStateException("Auth вернул данные другого продавца")
                )
            }

            return SellerShop(response.sellerId, response.companyName, response.description)
        } catch (exception: HttpClientErrorException.NotFound) {
            return null
        } catch (exception: RestClientException) {
            throw SellerShopUnavailableException(exception)
        } catch (exception: IllegalArgumentException) {
            throw SellerShopUnavailableException(exception)
        }
    }
}
