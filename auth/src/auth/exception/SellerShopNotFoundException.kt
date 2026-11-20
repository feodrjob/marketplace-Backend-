package auth.exception

/**
 * Сообщает об отсутствии публичного магазина.
 */
class SellerShopNotFoundException : AuthException(
    "Магазин продавца не найден"
)