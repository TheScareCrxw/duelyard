package com.duelyard.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class GameRegistryTest {

    private static GameModule module(String slug, String name) {
        GameDefinition def = new GameDefinition(slug, name, "", 2, 2, Set.of(GameMode.CASUAL), GameAvailability.AVAILABLE);
        return () -> def;
    }

    @Test
    void indexesModulesBySlugAndSortsByName() {
        GameRegistry registry = new GameRegistry(List.of(module("zeta", "Zeta"), module("alpha", "Alpha")));

        assertThat(registry.all()).extracting(GameDefinition::slug).containsExactly("alpha", "zeta");
        assertThat(registry.find("zeta")).isPresent();
        assertThat(registry.find("missing")).isEmpty();
    }

    @Test
    void rejectsDuplicateSlugs() {
        assertThatThrownBy(() -> new GameRegistry(List.of(module("dup", "A"), module("dup", "B"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("dup");
    }

    @Test
    void rejectsInvalidDefinitions() {
        assertThatThrownBy(() -> module("Bad Slug", "x"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameDefinition("ok", "x", "", 3, 2, Set.of(GameMode.CASUAL), GameAvailability.AVAILABLE))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameDefinition("ok", "x", "", 2, 2, Set.of(), GameAvailability.AVAILABLE))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
