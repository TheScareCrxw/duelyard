package com.duelyard.support;

import java.io.IOException;
import java.io.UncheckedIOException;

import javax.sql.DataSource;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Supplies a real PostgreSQL instance (same major version as docker-compose) to integration tests
 * without requiring Docker. One server is started per JVM and shared by all test contexts.
 * Flyway runs the production migrations against it, and Hibernate validates the schema.
 */
@TestConfiguration(proxyBeanMethods = false)
public class EmbeddedPostgresTestConfiguration {

    @Bean
    DataSource dataSource() {
        return Holder.POSTGRES.getPostgresDatabase();
    }

    private static final class Holder {
        static final EmbeddedPostgres POSTGRES = start();

        private static EmbeddedPostgres start() {
            try {
                EmbeddedPostgres pg = EmbeddedPostgres.builder().start();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        pg.close();
                    } catch (IOException ignored) {
                        // JVM is exiting.
                    }
                }));
                return pg;
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to start embedded PostgreSQL", e);
            }
        }
    }
}
