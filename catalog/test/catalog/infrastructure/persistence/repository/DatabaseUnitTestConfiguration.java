package catalog.infrastructure.persistence.repository;

import catalog.TestApplication;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = TestApplication.class)
@Testcontainers
public abstract class DatabaseUnitTestConfiguration {

    @Container
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("marketplace")
            .withUsername("postgres")
            .withPassword("postgres");

    @BeforeAll
    static void applyMigrations() {
        postgres.start();
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("catalog")
                .defaultSchema("catalog")
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> {
            String url = postgres.getJdbcUrl();
            return url.contains("?") ? url + "&currentSchema=catalog,public" : url + "?currentSchema=catalog,public";
        });
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "catalog");
    }
}
