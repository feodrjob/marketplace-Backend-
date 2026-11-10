package auth.infrastructure.persistence.repository;

import auth.TestApplication;
import auth.common.enums.Role;
import auth.infrastructure.persistence.entity.UserEntity;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class)
@Testcontainers
class UserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("marketplace")
            .withUsername("postgres")
            .withPassword("postgres");

    @BeforeAll
    static void applyMigrations() {
        postgres.start();
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("auth")
                .defaultSchema("auth")
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "auth");
    }

    @Autowired
    private UserRepository userRepository;

    @Nested
    @DisplayName("Позитивные сценарии (CRUD и поиск)")
    class PositiveScenarios {

        @Test
        @DisplayName("Сохранение и поиск покупателя по email и id")
        void shouldSaveAndFindBuyer() {
            UUID id = UUID.randomUUID();
            String email = "buyer_" + id + "@example.com";

            UserEntity buyer = new UserEntity(
                    id, email, "hash123", Role.BUYER,
                    "Алексей Покупатель", "+79991112233",
                    null, null, null, null,
                    Instant.now(), Instant.now()
            );

            userRepository.save(buyer);

            Optional<UserEntity> byEmail = userRepository.findByEmail(email);
            assertTrue(byEmail.isPresent());
            assertEquals("Алексей Покупатель", byEmail.get().getFullName());
            assertEquals(Role.BUYER, byEmail.get().getRole());

            Optional<UserEntity> byId = userRepository.findById(id);
            assertTrue(byId.isPresent());
            assertEquals(email, byId.get().getEmail());

            assertTrue(userRepository.existsByEmail(email));
            assertFalse(userRepository.existsByEmail("non_existent@example.com"));
        }

        @Test
        @DisplayName("Сохранение и поиск продавца с 10-значным ИНН (юрлицо)")
        void shouldSaveAndFindSeller10DigitInn() {
            UUID id = UUID.randomUUID();
            String email = "seller10_" + id + "@example.com";
            String inn = "7701234567";

            UserEntity seller = new UserEntity(
                    id, email, "hash123", Role.SELLER,
                    null, null, "ООО Альфа", inn, "Описание магазина", null,
                    Instant.now(), Instant.now()
            );

            userRepository.save(seller);

            Optional<UserEntity> found = userRepository.findByInn(inn);
            assertTrue(found.isPresent());
            assertEquals("ООО Альфа", found.get().getCompanyName());
            assertTrue(userRepository.existsByInn(inn));
        }

        @Test
        @DisplayName("Сохранение и поиск продавца с 12-значным ИНН (ИП/физлицо)")
        void shouldSaveAndFindSeller12DigitInn() {
            UUID id = UUID.randomUUID();
            String email = "seller12_" + id + "@example.com";
            String inn = "770123456789";

            UserEntity seller = new UserEntity(
                    id, email, "hash123", Role.SELLER,
                    null, null, "ИП Бета", inn, null, null,
                    Instant.now(), Instant.now()
            );

            userRepository.save(seller);

            Optional<UserEntity> found = userRepository.findByInn(inn);
            assertTrue(found.isPresent());
            assertEquals("ИП Бета", found.get().getCompanyName());
            assertTrue(userRepository.existsByInn(inn));
        }

        @Test
        @DisplayName("Сохранение администратора с департаментом")
        void shouldSaveAndFindAdmin() {
            UUID id = UUID.randomUUID();
            String email = "admin_" + id + "@example.com";

            UserEntity admin = new UserEntity(
                    id, email, "hash123", Role.ADMIN,
                    null, null, null, null, null, "Отдел безопасности",
                    Instant.now(), Instant.now()
            );

            userRepository.save(admin);

            Optional<UserEntity> found = userRepository.findByEmail(email);
            assertTrue(found.isPresent());
            assertEquals(Role.ADMIN, found.get().getRole());
            assertEquals("Отдел безопасности", found.get().getDepartment());
        }

        @Test
        @DisplayName("Множество покупателей могут иметь inn = null без коллизий")
        void shouldAllowMultipleBuyersWithNullInn() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();

            UserEntity b1 = new UserEntity(
                    id1, "b1_" + id1 + "@example.com", "hash", Role.BUYER,
                    null, null, null, null, null, null, Instant.now(), Instant.now()
            );
            UserEntity b2 = new UserEntity(
                    id2, "b2_" + id2 + "@example.com", "hash", Role.BUYER,
                    null, null, null, null, null, null, Instant.now(), Instant.now()
            );

            assertDoesNotThrow(() -> {
                userRepository.save(b1);
                userRepository.save(b2);
            });
        }

        @Test
        @DisplayName("Обновление данных профиля пользователя")
        void shouldUpdateUserProfile() {
            UUID id = UUID.randomUUID();
            String email = "update_" + id + "@example.com";

            UserEntity buyer = new UserEntity(
                    id, email, "hash", Role.BUYER,
                    "Старое Имя", null, null, null, null, null,
                    Instant.now(), Instant.now()
            );
            userRepository.saveAndFlush(buyer);

            buyer.setFullName("Новое Имя");
            buyer.setPhone("+79998887766");
            userRepository.saveAndFlush(buyer);

            UserEntity updated = userRepository.findById(id).orElseThrow();
            assertEquals("Новое Имя", updated.getFullName());
            assertEquals("+79998887766", updated.getPhone());
        }
    }

    @Nested
    @DisplayName("Корнер-кейсы и негативные сценарии (ограничения целостности)")
    class ConstraintViolationScenarios {

        @Test
        @DisplayName("Ошибка при попытке сохранить дубликат email")
        void shouldFailOnDuplicateEmail() {
            String duplicateEmail = "duplicate@example.com";

            UserEntity first = new UserEntity(
                    UUID.randomUUID(), duplicateEmail, "hash1", Role.BUYER,
                    null, null, null, null, null, null, Instant.now(), Instant.now()
            );
            userRepository.saveAndFlush(first);

            UserEntity second = new UserEntity(
                    UUID.randomUUID(), duplicateEmail, "hash2", Role.BUYER,
                    null, null, null, null, null, null, Instant.now(), Instant.now()
            );

            assertThrows(DataIntegrityViolationException.class, () -> {
                userRepository.saveAndFlush(second);
            });
        }

        @Test
        @DisplayName("Ошибка при попытке сохранить двух продавцов с одинаковым ИНН")
        void shouldFailOnDuplicateInn() {
            String duplicateInn = "7711223344";

            UserEntity s1 = new UserEntity(
                    UUID.randomUUID(), "sellerA@example.com", "hash", Role.SELLER,
                    null, null, "Компания А", duplicateInn, null, null, Instant.now(), Instant.now()
            );
            userRepository.saveAndFlush(s1);

            UserEntity s2 = new UserEntity(
                    UUID.randomUUID(), "sellerB@example.com", "hash", Role.SELLER,
                    null, null, "Компания Б", duplicateInn, null, null, Instant.now(), Instant.now()
            );

            assertThrows(DataIntegrityViolationException.class, () -> {
                userRepository.saveAndFlush(s2);
            });
        }

        @Test
        @DisplayName("Ошибка валидации формата ИНН: не 10 и не 12 цифр (например, 7 цифр)")
        void shouldFailOnInvalidInnLength() {
            UserEntity invalidSeller = new UserEntity(
                    UUID.randomUUID(), "invalid_inn@example.com", "hash", Role.SELLER,
                    null, null, "Компания", "1234567", null, null, Instant.now(), Instant.now()
            );

            assertThrows(DataIntegrityViolationException.class, () -> {
                userRepository.saveAndFlush(invalidSeller);
            });
        }

        @Test
        @DisplayName("Ошибка: продавец не может быть создан без названия компании")
        void shouldFailWhenSellerHasNoCompanyName() {
            UserEntity invalidSeller = new UserEntity(
                    UUID.randomUUID(), "no_company@example.com", "hash", Role.SELLER,
                    null, null, null, "7701122334", null, null, Instant.now(), Instant.now()
            );

            assertThrows(DataIntegrityViolationException.class, () -> {
                userRepository.saveAndFlush(invalidSeller);
            });
        }

        @Test
        @DisplayName("Ошибка: продавец не может быть создан без ИНН")
        void shouldFailWhenSellerHasNoInn() {
            UserEntity invalidSeller = new UserEntity(
                    UUID.randomUUID(), "no_inn@example.com", "hash", Role.SELLER,
                    null, null, "ООО Компания", null, null, null, Instant.now(), Instant.now()
            );

            assertThrows(DataIntegrityViolationException.class, () -> {
                userRepository.saveAndFlush(invalidSeller);
            });
        }

        @Test
        @DisplayName("Ошибка: email не может быть null")
        void shouldFailWhenEmailIsNull() {
            UserEntity nullEmail = new UserEntity(
                    UUID.randomUUID(), null, "hash", Role.BUYER,
                    null, null, null, null, null, null, Instant.now(), Instant.now()
            );

            assertThrows(DataIntegrityViolationException.class, () -> {
                userRepository.saveAndFlush(nullEmail);
            });
        }
    }
}
