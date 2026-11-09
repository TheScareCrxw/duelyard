package com.duelyard.game;

import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Fails startup if a registered {@link GameModule} has no row in the {@code games} table, so a new
 * game can't ship without the migration that ratings and matches depend on.
 */
@Component
class GameCatalogVerifier implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GameCatalogVerifier.class);

    private final GameRegistry registry;
    private final GameRepository repository;

    GameCatalogVerifier(GameRegistry registry, GameRepository repository) {
        this.registry = registry;
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        Set<String> persisted = repository.findAll().stream().map(Game::getSlug).collect(Collectors.toSet());
        Set<String> registered = registry.all().stream().map(GameDefinition::slug).collect(Collectors.toSet());

        Set<String> missing = registered.stream().filter(s -> !persisted.contains(s)).collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "Game modules without a 'games' table row (add a Flyway migration): " + missing);
        }

        persisted.stream()
            .filter(s -> !registered.contains(s))
            .forEach(s -> log.warn("Game '{}' exists in the database but has no GameModule", s));

        log.info("Game catalog verified: {}", registered);
    }
}
