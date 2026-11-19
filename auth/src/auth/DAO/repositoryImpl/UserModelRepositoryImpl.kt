package auth.DAO.repositoryImpl

import auth.DAO.mapper.UserEntityMapper
import auth.domain.model.UserModel
import auth.domain.repository.UserModelRepository
import auth.infrastructure.persistence.repository.UserRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.Optional
import java.util.UUID
import auth.exception.EmailAlreadyUsedException
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import auth.exception.InnAlreadyUsedException

@Repository
@Transactional(readOnly = true)
class UserModelRepositoryImpl(
    private val entities: UserRepository,
    private val mapper: UserEntityMapper
) : UserModelRepository {

    override fun findById(id: UUID): Optional<UserModel> {   //(name: type)
        return entities.findById(id).map(mapper::toDomain)
    }

    override fun findByEmail(email: String): Optional<UserModel> {
        return entities.findByEmail(email).map(mapper::toDomain)
    }

    override fun existsByEmail(email: String): Boolean {
        return entities.existsByEmail(email)
    }


    @Transactional
    override fun save(user: UserModel): UserModel {
        val existing = entities.findById(user.id).orElse(null)

        val entity = if (existing == null) {
            mapper.toNewEntity(user)
        } else {
            mapper.updateProfile(user, existing)

            existing
        }

        val saved = try {
            entities.saveAndFlush(entity)
        } catch (exception: DataIntegrityViolationException) {
            val constraintName = findViolatedConstraint(exception)

            if (constraintName == "users_email_key") {
                val conflict = EmailAlreadyUsedException(
                    "Пользователь с таким email уже зарегистрирован."
                )

                conflict.initCause(exception)
                throw conflict
            }

            if (constraintName == "seller_profiles_inn_key") {
                val conflict = InnAlreadyUsedException(
                    "Продавец с таким инн уже зарегистрирован"
                )

                conflict.initCause(exception)
                throw conflict
            }
            throw exception
        }

        return mapper.toDomain(saved)
    }

    private fun findViolatedConstraint(exception: DataIntegrityViolationException): String? {
        var current: Throwable? = exception

        while (current != null) {
            if (current is ConstraintViolationException) {
                return current.constraintName
            }

            current = current.cause

        }

        return null

    }

}