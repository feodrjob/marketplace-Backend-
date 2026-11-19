package auth.DAO.mapper

import auth.common.enums.Role
import auth.domain.model.UserModel
import auth.infrastructure.persistence.entity.UserEntity
import org.springframework.stereotype.Component
import auth.infrastructure.persistence.entity.BuyerProfileEntity
import auth.infrastructure.persistence.entity.SellerProfileEntity

@Component
class UserEntityMapper {
    fun toDomain(entity: UserEntity): UserModel {
        val role = checkNotNull(entity.role) {
            "У сохранённого пользователя отсутствует роль"
        }

        val buyer = entity.buyerProfile
        val seller = entity.sellerProfile

        when (role) {
            Role.BUYER -> checkNotNull(buyer) {
                "У покупателя ${entity.id} отсутствует профиль"
            }
            Role.SELLER -> checkNotNull(seller) {
                "У продавца ${entity.id} отсутствует профиль"
            }
            Role.ADMIN -> Unit
        }

        return UserModel.restore(
            checkNotNull(entity.id){
                "У сохраненного пользователя отсутствует идентификатор"
            },
            entity.email,
            entity.passwordHash,
            role,
            buyer?.fullName,
            buyer?.phone,
            seller?.companyName,
            seller?.inn,
            seller?.description,
            entity.department,
            checkNotNull(entity.createdAt) {
                "У сохраненного пользователя отсутствует дата создания "
            },
            checkNotNull(entity.updatedAt) {
                "У сохраненного пользователя отсутствует  дата обновления"
            }


        )

    }

    fun toNewEntity(user: UserModel): UserEntity {
        val entity = UserEntity()

        entity.id = user.id
        entity.email = user.email
        entity.passwordHash = user.passwordHash
        entity.role = user.role
        entity.department = user.department

        when (user.role) {
            Role.BUYER -> {
                val profile = BuyerProfileEntity()
                profile.fullName = user.fullName
                profile.phone = user.phone

                entity.setBuyerProfile(profile)
            }

            Role.SELLER -> {
                val profile = SellerProfileEntity()
                profile.companyName = user.companyName
                profile.inn = user.inn
                profile.description = user.description

                entity.setSellerProfile(profile)
            }

            Role.ADMIN -> Unit
        }

        return entity
    }

    fun updateProfile(user: UserModel, entity: UserEntity) {
        check(entity.id == user.id) {
            "Нельзя обновить сущность другого пользователя"
        }
        check(entity.role == user.role) {
            "Нельзя изменить роль через обновление профиля"
        }

        when (user.role) {
            Role.BUYER -> {
                val profile = checkNotNull(entity.buyerProfile) {
                    "У покупателя ${entity.id} отсутствует профиль"
                }

                profile.fullName = user.fullName
                profile.phone = user.phone
            }

            Role.SELLER -> {
                val profile = checkNotNull(entity.sellerProfile) {
                    "У продавца ${entity.id} отсутствует профиль"
                }

                profile.companyName = user.companyName
                profile.description = user.description
            }

            Role.ADMIN -> Unit
        }

        entity.updatedAt = user.updatedAt
    }
}