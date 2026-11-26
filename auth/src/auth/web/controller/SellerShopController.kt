package auth.web.controller

import auth.domain.service.UserService
import auth.web.dto.user.SellerShopResponse
import auth.web.mapper.AuthDtoMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Отдаёт публичные данные магазина.
 */
@RestController
@RequestMapping("/api/v1/sellers")
@Tag(name = "Sellers", description = "Публичные данные магазинов")
class SellerShopController(
    private val userService: UserService,
    private val mapper: AuthDtoMapper,
) {

    // Возвращает публичные данные магазина.
    @GetMapping("/{sellerId}/shop")
    @Operation(summary = "Получить публичные данные магазина")
    @ApiResponse(responseCode = "200", description = "Магазин найден")
    @ApiResponse(responseCode = "400", description = "Некорректный UUID")
    @ApiResponse(responseCode = "404", description = "Магазин не найден")
    fun getShop(
        @PathVariable(name = "sellerId") sellerId: UUID,
    ): SellerShopResponse {
        val profile = userService.getSellerShop(sellerId)

        return mapper.toSellerShopResponse(profile)
    }
}