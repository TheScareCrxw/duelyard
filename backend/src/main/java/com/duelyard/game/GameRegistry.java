package com.duelyard.game;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Collects every {@link GameModule} bean and indexes it by slug. Fails fast at startup if two
 * modules claim the same slug.
 */
@Component
public class GameRegistry {

    private final Map<String, GameModule> modulesBySlug;
    private final List<GameDefinition> orderedDefinitions;

    public GameRegistry(List<GameModule> modules) {
        Map<String, GameModule> index = new LinkedHashMap<>();
        modules.stream()
            .sorted(Comparator.comparing(m -> m.definition().displayName()))
            .forEach(module -> {
                String slug = module.definition().slug();
                GameModule previous = index.putIfAbsent(slug, module);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate game slug '%s' registered by %s and %s"
                        .formatted(slug, previous.getClass().getName(), module.getClass().getName()));
                }
            });
        this.modulesBySlug = Map.copyOf(index);
        this.orderedDefinitions = index.values().stream().map(GameModule::definition).toList();
    }

    public List<GameDefinition> all() {
        return orderedDefinitions;
    }

    public Optional<GameModule> find(String slug) {
        return Optional.ofNullable(modulesBySlug.get(slug));
    }
}
