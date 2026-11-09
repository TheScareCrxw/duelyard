package com.duelyard.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Runs a real PostgreSQL 18 on localhost:5432 for local development, without Docker. It matches
 * docker-compose.yml (database, user and password {@code duelyard} / {@code duelyard_local}), so
 * the backend runs with its default settings.
 *
 * <p>Data persists in {@code backend/target/local-postgres} until {@code mvn clean}. Stop with Ctrl+C.
 *
 * <pre>
 *   cd backend
 *   ./mvnw test-compile exec:java -Dexec.mainClass=com.duelyard.support.LocalPostgres -Dexec.classpathScope=test
 * </pre>
 */
public final class LocalPostgres {

    private LocalPostgres() {
    }

    public static void main(String[] args) throws Exception {
        Path dataDir = Path.of("target", "local-postgres").toAbsolutePath();
        // A half-created directory (no PG_VERSION, e.g. after an interrupted first start) can't be
        // initialized into; it holds no data, so clear it.
        if (Files.isDirectory(dataDir) && !Files.exists(dataDir.resolve("PG_VERSION"))) {
            try (var leftovers = Files.walk(dataDir)) {
                leftovers.sorted(java.util.Comparator.reverseOrder())
                    .filter(p -> !p.equals(dataDir))
                    .forEach(p -> p.toFile().delete());
            }
        }
        Files.createDirectories(dataDir);

        EmbeddedPostgres postgres = EmbeddedPostgres.builder()
            .setPort(5432)
            .setDataDirectory(dataDir)
            .setCleanDataDirectory(false)
            .start();

        try (Connection c = postgres.getPostgresDatabase().getConnection(); Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("SELECT 1 FROM pg_roles WHERE rolname = 'duelyard'")) {
                if (!r.next()) {
                    s.execute("CREATE ROLE duelyard LOGIN PASSWORD 'duelyard_local'");
                }
            }
            try (ResultSet r = s.executeQuery("SELECT 1 FROM pg_database WHERE datname = 'duelyard'")) {
                if (!r.next()) {
                    s.execute("CREATE DATABASE duelyard OWNER duelyard");
                }
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                postgres.close();
            } catch (Exception ignored) {
                // Exiting anyway.
            }
        }));
        System.out.println("PostgreSQL ready on localhost:5432 (db/user: duelyard). Data: " + dataDir + ". Ctrl+C to stop.");
        Thread.currentThread().join();
    }
}
