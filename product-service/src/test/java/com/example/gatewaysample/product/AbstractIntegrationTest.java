package com.example.gatewaysample.product;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton container pattern: started once and never stopped by JUnit, so it survives across
 * this module's multiple {@code @SpringBootTest} classes. Using {@code @Testcontainers}/{@code
 * @Container} instead stops the container after each class's {@code afterAll}, which breaks any
 * later class whose identical Spring context configuration causes the test context to be reused
 * from the cache with a now-dead connection pool.
 */
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17.11-alpine"));

    static {
        POSTGRES.start();
    }
}
