package auth.infrastructure.persistence.repository;

import auth.common.enums.Role;
import auth.infrastructure.persistence.entity.BuyerProfileEntity;
import auth.infrastructure.persistence.entity.SellerProfileEntity;
import auth.infrastructure.persistence.entity.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserRepositoryTest extends DatabaseUnitTestConfiguration {

    @Autowired
    private UserRepository userRepository;

    @Nested
    @DisplayName("Позитивные сценарии (CRUD и поиск)")
    class PositiveScenarios {

        @Test
        @DisplayName("Сохранение и поиск покупателя с профилем")
        void shouldSaveAndFindBuyerWithProfile() {
            UUID userId = UUID.randomUUID();
            String email = "buyer_" + userId + "@example.com";

            UserEntity buyer = UserEntity.builder()
                    .id(userId)
                    .email(email)
                    .passwordHash("$2a$10$hashedPasswordValue")
                    .role(Role.BUYER)
                    .build();

            BuyerProfileEntity buyerProfile = BuyerProfileEntity.builder()
                    .id(UUID.randomUUID())
                    .fullName("Иван Иванов")
                    .phone("+79991112233")
                    .build();

            buyer.setBuyerProfile(buyerProfile);

            userRepository.save(buyer);

            Optional<UserEntity> found = userRepository.findByEmail(email);
            assertTrue(found.isPresent());
            assertEquals(Role.BUYER, found.get().getRole());
            assertNotNull(found.get().getBuyerProfile());
            assertEquals("Иван Иванов", found.get().getBuyerProfile().getFullName());
            assertEquals("+79991112233", found.get().getBuyerProfile().getPhone());

            assertTrue(userRepository.existsByEmail(email));
            assertFalse(userRepository.existsByEmail("unknown@example.com"));
        }

        @Test
        @DisplayName("Сохранение и поиск продавца по ИНН")
        void shouldSaveAndFindSellerByInn() {
            UUID userId = UUID.randomUUID();
            String email = "seller_" + userId + "@example.com";
            String inn = "770" + (System.currentTimeMillis() % 10000000L);

            UserEntity seller = UserEntity.builder()
                    .id(userId)
                    .email(email)
                    .passwordHash("$2a$10$hashedPasswordValue")
                    .role(Role.SELLER)
                    .build();

            SellerProfileEntity sellerProfile = SellerProfileEntity.builder()
                    .id(UUID.randomUUID())
                    .companyName("ООO Маркет Трейд")
                    .inn(inn)
                    .description("Магазин электроники")
                    .build();

            seller.setSellerProfile(sellerProfile);

            userRepository.save(seller);

            Optional<UserEntity> found = userRepository.findBySellerInn(inn);
            assertTrue(found.isPresent());
            assertEquals(email, found.get().getEmail());
            assertEquals(Role.SELLER, found.get().getRole());
            assertEquals("ООO Маркет Трейд", found.get().getSellerProfile().getCompanyName());

            assertTrue(userRepository.existsBySellerInn(inn));
        }

        @Test
        @DisplayName("Сохранение администратора с департаментом")
        void shouldSaveAndFindAdmin() {
            UUID userId = UUID.randomUUID();
            String email = "admin_" + userId + "@example.com";

            UserEntity admin = UserEntity.builder()
                    .id(userId)
                    .email(email)
                    .passwordHash("$2a$10$hashedPasswordValue")
                    .role(Role.ADMIN)
                    .department("Департамент модерации")
                    .build();

            userRepository.save(admin);

            Optional<UserEntity> found = userRepository.findByEmail(email);
            assertTrue(found.isPresent());
            assertEquals(Role.ADMIN, found.get().getRole());
            assertEquals("Департамент модерации", found.get().getDepartment());
            assertNull(found.get().getBuyerProfile());
            assertNull(found.get().getSellerProfile());
        }

        @Test
        @DisplayName("Каскадное удаление пользователя")
        void shouldCascadeDeleteUser() {
            UUID userId = UUID.randomUUID();

            UserEntity user = UserEntity.builder()
                    .id(userId)
                    .email("cascade_" + userId + "@example.com")
                    .passwordHash("hash")
                    .role(Role.BUYER)
                    .build();

            BuyerProfileEntity profile = BuyerProfileEntity.builder()
                    .id(UUID.randomUUID())
                    .fullName("Удаляемый Профиль")
                    .build();

            user.setBuyerProfile(profile);
            userRepository.saveAndFlush(user);

            assertTrue(userRepository.findById(userId).isPresent());

            userRepository.delete(user);
            userRepository.flush();

            assertFalse(userRepository.findById(userId).isPresent());
        }
    }

    @Nested
    @DisplayName("Корнер-кейсы и негативные сценарии (ограничения целостности)")
    class ConstraintViolationScenarios {

        @Test
        @DisplayName("Ошибка при попытке сохранить дубликат email")
        void shouldFailOnDuplicateEmail() {
            String duplicateEmail = "duplicate@example.com";

            UserEntity first = UserEntity.builder()
                    .id(UUID.randomUUID())
                    .email(duplicateEmail)
                    .passwordHash("hash1")
                    .role(Role.BUYER)
                    .build();
            userRepository.saveAndFlush(first);

            UserEntity second = UserEntity.builder()
                    .id(UUID.randomUUID())
                    .email(duplicateEmail)
                    .passwordHash("hash2")
                    .role(Role.BUYER)
                    .build();

            assertThrows(DataIntegrityViolationException.class, () -> {
                userRepository.saveAndFlush(second);
            });
        }

        @Test
        @DisplayName("Ошибка при попытке сохранить двух продавцов с одинаковым ИНН")
        void shouldFailOnDuplicateInn() {
            String duplicateInn = "7711998877";

            UserEntity s1 = UserEntity.builder()
                    .id(UUID.randomUUID())
                    .email("seller1_" + UUID.randomUUID() + "@example.com")
                    .passwordHash("hash")
                    .role(Role.SELLER)
                    .build();
            s1.setSellerProfile(SellerProfileEntity.builder()
                    .id(UUID.randomUUID())
                    .companyName("Компания 1")
                    .inn(duplicateInn)
                    .build());
            userRepository.saveAndFlush(s1);

            UserEntity s2 = UserEntity.builder()
                    .id(UUID.randomUUID())
                    .email("seller2_" + UUID.randomUUID() + "@example.com")
                    .passwordHash("hash")
                    .role(Role.SELLER)
                    .build();
            s2.setSellerProfile(SellerProfileEntity.builder()
                    .id(UUID.randomUUID())
                    .companyName("Компания 2")
                    .inn(duplicateInn)
                    .build());

            assertThrows(DataIntegrityViolationException.class, () -> {
                userRepository.saveAndFlush(s2);
            });
        }

        @Test
        @DisplayName("Ошибка: продавец не может быть создан без названия компании")
        void shouldFailWhenSellerHasNoCompanyName() {
            UserEntity invalidSeller = UserEntity.builder()
                    .id(UUID.randomUUID())
                    .email("no_company_" + UUID.randomUUID() + "@example.com")
                    .passwordHash("hash")
                    .role(Role.SELLER)
                    .build();
            invalidSeller.setSellerProfile(SellerProfileEntity.builder()
                    .id(UUID.randomUUID())
                    .inn("7701122334")
                    .build());

            assertThrows(DataIntegrityViolationException.class, () -> {
                userRepository.saveAndFlush(invalidSeller);
            });
        }

        @Test
        @DisplayName("Ошибка: продавец не может быть создан без ИНН")
        void shouldFailWhenSellerHasNoInn() {
            UserEntity invalidSeller = UserEntity.builder()
                    .id(UUID.randomUUID())
                    .email("no_inn_" + UUID.randomUUID() + "@example.com")
                    .passwordHash("hash")
                    .role(Role.SELLER)
                    .build();
            invalidSeller.setSellerProfile(SellerProfileEntity.builder()
                    .id(UUID.randomUUID())
                    .companyName("ООО Компания")
                    .build());

            assertThrows(DataIntegrityViolationException.class, () -> {
                userRepository.saveAndFlush(invalidSeller);
            });
        }
    }
}
