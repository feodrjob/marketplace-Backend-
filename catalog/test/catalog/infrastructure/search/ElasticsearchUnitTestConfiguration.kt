package catalog.infrastructure.search

import catalog.infrastructure.persistence.repository.DatabaseUnitTestConfiguration
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

/**
 * Базовая конфигурация интеграционных тестов поиска на Testcontainers (PostgreSQL + Elasticsearch).
 */
abstract class ElasticsearchUnitTestConfiguration : DatabaseUnitTestConfiguration() {

    @Autowired
    protected lateinit var elasticsearchOperations: ElasticsearchOperations

    @Autowired
    protected lateinit var indexRepository: ElasticsearchProductIndexRepository

    @BeforeEach
    fun resetIndex() {
        val indexOps = elasticsearchOperations.indexOps(ProductDocument::class.java)
        if (indexOps.exists()) {
            indexOps.delete()
        }
        indexRepository.ensureIndexExists()
    }

    companion object {
        private const val ELASTICSEARCH_IMAGE = "docker.elastic.co/elasticsearch/elasticsearch:9.0.0"
        private const val HTTP_PORT = 9200

        @JvmStatic
        protected val elasticsearch: GenericContainer<*> = GenericContainer(DockerImageName.parse(ELASTICSEARCH_IMAGE))
            .withExposedPorts(HTTP_PORT)
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m")
            .waitingFor(Wait.forHttp("/_cluster/health").forStatusCode(200))

        init {
            elasticsearch.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureElasticsearchProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.elasticsearch.uris") {
                "http://${elasticsearch.host}:${elasticsearch.getMappedPort(HTTP_PORT)}"
            }
        }
    }
}
