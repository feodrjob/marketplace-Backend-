package catalog.infrastructure.persistence.repository

import catalog.TestApplication
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

@SpringBootTest(classes = [TestApplication::class])
abstract class DatabaseUnitTestConfiguration {

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE catalog.product_images, catalog.products, catalog.categories CASCADE;")
    }

    companion object {
        @JvmStatic
        protected val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("marketplace")
            .withUsername("postgres")
            .withPassword("postgres")

        init {
            postgres.start()
            val flyway = Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .schemas("catalog")
                .defaultSchema("catalog")
                .locations("classpath:db/migration")
                .load()
            flyway.migrate()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") {
                val url = postgres.jdbcUrl
                if (url.contains("?")) "$url&currentSchema=catalog,public" else "$url?currentSchema=catalog,public"
            }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
            registry.add("spring.jpa.properties.hibernate.default_schema") { "catalog" }
            registry.add("spring.jpa.open-in-view") { "false" }
        }
    }
}
