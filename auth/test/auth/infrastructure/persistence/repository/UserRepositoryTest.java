package auth.infrastructure.persistence.repository;

import auth.TestApplication;
import auth.common.enums.Role;
import auth.infrastructure.persistence.entity.UserEntity;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    @DisplayName("Должен успешно сохранять покупателя и находить его по email")
    void shouldSaveAndFindBuyerByEmail() {
        UUID userId = UUID.randomUUID();
        UserEntity buyer = new UserEntity(
                userId,
                "buyer.test@example.com",
                "$2a$10$hashedPasswordValue",
                Role.BUYER,
                "Иван Тестовый",
                "+79990001122",
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now()
        );

        userRepository.save(buyer);

        Optional<UserEntity> found = userRepository.findByEmail("buyer.test@example.com");
        assertTrue(found.isPresent());
        assertEquals("Иван Тестовый", found.get().getFullName());
        assertEquals(Role.BUYER, found.get().getRole());
        assertTrue(userRepository.existsByEmail("buyer.test@example.com"));
    }

    @Test
    @DisplayName("Должен успешно сохранять продавца и находить его по ИНН")
    void shouldSaveAndFindSellerByInn() {
        UUID sellerId = UUID.randomUUID();
        UserEntity seller = new UserEntity(
                sellerId,
                "seller.test@example.com",
                "$2a$10$hashedPasswordValue",
                Role.SELLER,
                null,
                null,
                "ООО Рога и Копыта",
                "7709876543",
                "Описание магазина",
                null,
                Instant.now(),
                Instant.now()
        );

        userRepository.save(seller);

        Optional<UserEntity> found = userRepository.findByInn("7709876543");
        assertTrue(found.isPresent());
        assertEquals("ООО Рога и Копыта", found.get().getCompanyName());
        assertEquals(Role.SELLER, found.get().getRole());
        assertTrue(userRepository.existsByInn("7709876543"));
    }
}
