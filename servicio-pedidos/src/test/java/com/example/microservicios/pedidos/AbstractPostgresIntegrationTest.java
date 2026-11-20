package com.example.microservicios.pedidos;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need a real PostgreSQL 16 server.
 *
 * <p>The container is started once in a static initialiser and is deliberately NOT annotated with
 * {@code @Container}: the JUnit extension stops managed containers at the end of each test class,
 * which would leave the cached Spring context pointing at a dead port when the next integration test
 * class reuses it. Starting it here keeps one server alive for the whole module JVM; Ryuk removes it
 * when the JVM exits. {@code disabledWithoutDocker = true} skips those tests on machines without
 * Docker instead of failing them, which keeps {@code mvn verify} usable outside the lab environment.
 */
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
public abstract class AbstractPostgresIntegrationTest {

    /** Name of the database owned by this service. The other service database must not exist here. */
    public static final String DATABASE_NAME = "pedidos_db";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
            .withDatabaseName(DATABASE_NAME)
            .withUsername("pedidos_app")
            .withPassword("pedidos_app");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
