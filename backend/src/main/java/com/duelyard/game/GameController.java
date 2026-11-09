package com.duelyard.game;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.duelyard.common.error.NotFoundException;
import com.duelyard.game.dto.GameSummaryResponse;

/** Public catalog of games. */
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameRegistry registry;

    public GameController(GameRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<GameSummaryResponse> list() {
        return registry.all().stream().map(GameSummaryResponse::from).toList();
    }

    @GetMapping("/{slug}")
    public GameSummaryResponse get(@PathVariable String slug) {
        return registry.find(slug)
            .map(module -> GameSummaryResponse.from(module.definition()))
            .orElseThrow(() -> new NotFoundException("Unknown game: " + slug));
    }
}
