package auth.domain.service

import auth.common.enums.Role
import auth.domain.model.SellerShop
import auth.domain.repository.UserModelRepository
import org.springframework.stereotype.Service
import java.util.UUID

/*
* Возвращает публичные данные магазина по идентификатору аккаунта продавца
* */
@Service
class PublicSellerService(
    private val users: UserModelRepository,
) {
    fun findById(id: UUID): SellerShop? {
        val user = users.findById(id).orElse(null) ?: return null
        if (user.role != Role.SELLER) return null
        return SellerShop(user.id, checkNotNull(user.companyName), user.description)
    }
}
