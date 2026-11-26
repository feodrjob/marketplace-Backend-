package catalog.infrastructure.auth

import catalog.domain.model.SellerShop
import catalog.domain.repository.SellerShopLookup
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.util.UUID

/*
* Получает публичное название и описание магазина из Auth
* */
@Component
class HttpSellerShopLookup(
    @param:Qualifier("catalogAuthClient") private val client: RestClient,
) : SellerShopLookup {
    override fun findBySellerId(sellerId: UUID): SellerShop? {
        val response = try {
            client.get().uri("/api/v1/sellers/{id}", sellerId)
                .retrieve().body(SellerResponse::class.java)
        } catch (exception: RestClientResponseException) {
            if (exception.statusCode == HttpStatus.NOT_FOUND) return null
            throw exception
        } ?: return null
        check(response.sellerId == sellerId) { "Seller response does not match the requested account" }
        return SellerShop(response.sellerId, response.companyName, response.description)
    }

    private data class SellerResponse(
        val sellerId: UUID,
        val companyName: String,
        val description: String?,
    )
}
