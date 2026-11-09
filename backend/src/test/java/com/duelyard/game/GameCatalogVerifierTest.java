package com.duelyard.game;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class GameCatalogVerifierTest {

    private static GameModule module(String slug) {
        GameDefinition def = new GameDefinition(slug, slug, "", 2, 2, Set.of(GameMode.CASUAL), GameAvailability.AVAILABLE);
        return () -> def;
    }

    private static Game game(String slug) {
        Game g = mock(Game.class);
        when(g.getSlug()).thenReturn(slug);
        return g;
    }

    @Test
    void failsWhenModuleHasNoDatabaseRow() {
        GameRepository repo = mock(GameRepository.class);
        List<Game> rows = List.of(game("darts"));
        when(repo.findAll()).thenReturn(rows);
        var verifier = new GameCatalogVerifier(new GameRegistry(List.of(module("darts"), module("bowling"))), repo);

        assertThatThrownBy(() -> verifier.run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("bowling");
    }

    @Test
    void toleratesDatabaseRowWithoutModule() {
        GameRepository repo = mock(GameRepository.class);
        List<Game> rows = List.of(game("darts"), game("retired-game"));
        when(repo.findAll()).thenReturn(rows);
        var verifier = new GameCatalogVerifier(new GameRegistry(List.of(module("darts"))), repo);

        assertThatCode(() -> verifier.run(null)).doesNotThrowAnyException();
    }
}
