package auth.web.controller

import auth.domain.service.PublicSellerService
import auth.web.dto.seller.SellerResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/*
* Предоставляет публичные данные магазина для витрины каталога
* */
@RestController
@RequestMapping("/api/v1/sellers")
class SellerController(
    private val publicSellerService: PublicSellerService,
) {
    @GetMapping("/{id}")
    fun getSeller(@PathVariable("id") id: UUID): SellerResponse {
        val shop = publicSellerService.findById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Seller is not available")
        return SellerResponse(shop.sellerId, shop.companyName, shop.description)
    }
}
