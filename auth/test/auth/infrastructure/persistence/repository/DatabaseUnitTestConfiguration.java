package auth.infrastructure.persistence.repository;

import auth.TestApplication;
import org.flywaydb.core.Flyway;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = TestApplication.class)
public abstract class DatabaseUnitTestConfiguration {

    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("marketplace")
            .withUsername("postgres")
            .withPassword("postgres");

    static {
        postgres.start();
        Flyway.configure()
                .dataSource(jdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("auth")
                .defaultSchema("auth")
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DatabaseUnitTestConfiguration::jdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "auth");
    }

    private static String jdbcUrl() {
        return postgres.getJdbcUrl().replace("localhost", "127.0.0.1");
    }
}
