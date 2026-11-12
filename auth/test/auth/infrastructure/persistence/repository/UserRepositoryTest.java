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
            String email = "buyer_" + UUID.randomUUID() + "@example.com";

            UserEntity buyer = UserEntity.builder()
                    .email(email)
                    .passwordHash("$2a$10$hashedPasswordValue")
                    .role(Role.BUYER)
                    .build();

            BuyerProfileEntity buyerProfile = BuyerProfileEntity.builder()
                    .fullName("Иван Иванов")
                    .phone("+79991112233")
                    .build();

            buyer.setBuyerProfile(buyerProfile);

            UserEntity saved = userRepository.save(buyer);
            assertNotNull(saved.getId());

            Optional<UserEntity> found = userRepository.findByEmail(email);
            assertTrue(found.isPresent());
            assertEquals(Role.BUYER, found.get().getRole());
            assertNotNull(found.get().getBuyerProfile());
            assertNotNull(found.get().getBuyerProfile().getId());
            assertEquals("Иван Иванов", found.get().getBuyerProfile().getFullName());
            assertEquals("+79991112233", found.get().getBuyerProfile().getPhone());

            assertTrue(userRepository.existsByEmail(email));
            assertFalse(userRepository.existsByEmail("unknown@example.com"));
        }

        @Test
        @DisplayName("Сохранение и поиск продавца по ИНН")
        void shouldSaveAndFindSellerByInn() {
            String email = "seller_" + UUID.randomUUID() + "@example.com";
            String inn = "770" + (System.currentTimeMillis() % 10000000L);

            UserEntity seller = UserEntity.builder()
                    .email(email)
                    .passwordHash("$2a$10$hashedPasswordValue")
                    .role(Role.SELLER)
                    .build();

            SellerProfileEntity sellerProfile = SellerProfileEntity.builder()
                    .companyName("ООO Маркет Трейд")
                    .inn(inn)
                    .description("Магазин электроники")
                    .build();

            seller.setSellerProfile(sellerProfile);

            UserEntity saved = userRepository.save(seller);
            assertNotNull(saved.getId());

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
            String email = "admin_" + UUID.randomUUID() + "@example.com";

            UserEntity admin = UserEntity.builder()
                    .email(email)
                    .passwordHash("$2a$10$hashedPasswordValue")
                    .role(Role.ADMIN)
                    .department("Департамент модерации")
                    .build();

            UserEntity saved = userRepository.save(admin);
            assertNotNull(saved.getId());

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
            UserEntity user = UserEntity.builder()
                    .email("cascade_" + UUID.randomUUID() + "@example.com")
                    .passwordHash("hash")
                    .role(Role.BUYER)
                    .build();

            BuyerProfileEntity profile = BuyerProfileEntity.builder()
                    .fullName("Удаляемый Профиль")
                    .build();

            user.setBuyerProfile(profile);
            UserEntity saved = userRepository.saveAndFlush(user);

            UUID userId = saved.getId();
            assertTrue(userRepository.findById(userId).isPresent());

            userRepository.delete(saved);
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
                    .email(duplicateEmail)
                    .passwordHash("hash1")
                    .role(Role.BUYER)
                    .build();
            userRepository.saveAndFlush(first);

            UserEntity second = UserEntity.builder()
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
                    .email("seller1_" + UUID.randomUUID() + "@example.com")
                    .passwordHash("hash")
                    .role(Role.SELLER)
                    .build();
            s1.setSellerProfile(SellerProfileEntity.builder()
                    .companyName("Компания 1")
                    .inn(duplicateInn)
                    .build());
            userRepository.saveAndFlush(s1);

            UserEntity s2 = UserEntity.builder()
                    .email("seller2_" + UUID.randomUUID() + "@example.com")
                    .passwordHash("hash")
                    .role(Role.SELLER)
                    .build();
            s2.setSellerProfile(SellerProfileEntity.builder()
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
                    .email("no_company_" + UUID.randomUUID() + "@example.com")
                    .passwordHash("hash")
                    .role(Role.SELLER)
                    .build();
            invalidSeller.setSellerProfile(SellerProfileEntity.builder()
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
                    .email("no_inn_" + UUID.randomUUID() + "@example.com")
                    .passwordHash("hash")
                    .role(Role.SELLER)
                    .build();
            invalidSeller.setSellerProfile(SellerProfileEntity.builder()
                    .companyName("ООО Компания")
                    .build());

            assertThrows(DataIntegrityViolationException.class, () -> {
                userRepository.saveAndFlush(invalidSeller);
            });
        }
    }
}
