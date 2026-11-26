package catalog.infrastructure.auth

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

/**
 * Описывает публичный ответ сервиса Auth.
 */
data class SellerShopResponse @JsonCreator constructor(
    @param:JsonProperty("sellerId") val sellerId: UUID,
    @param:JsonProperty("companyName") val companyName: String,
    @param:JsonProperty("description") val description: String?,
)
