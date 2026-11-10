package com.duelyard.game.darts;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

import com.duelyard.game.engine.GameSession;
import com.duelyard.game.engine.Inputs;
import com.duelyard.game.engine.InvalidActionException;
import com.duelyard.game.engine.TwoPlayerOutcomes;
import com.duelyard.match.MatchResult;

import tools.jackson.databind.JsonNode;

/** 301, straight out, 3 darts a turn, 10 rounds. See docs/games.md. */
public class DartsSession implements GameSession {

    public static final int START_SCORE = 301;
    public static final int DARTS_PER_TURN = 3;
    public static final int MAX_ROUNDS = 10;
    static final double BASE_SIGMA = 9;
    static final double MAX_EXTRA_SIGMA = 21;

    /** One dart. {@code aimX/aimY} is where the player aimed, {@code x/y} where the server landed it. */
    public record Throw(int seat, double aimX, double aimY, double x, double y, int points, String label,
                        boolean bust, int remaining, boolean finished) {
    }

    /** The turn passed to the other player. */
    public record TurnEnded(int seat, int scored, boolean bust) {
    }

    public record View(int[] scores, int round, int maxRounds, int turnSeat, int dartsLeft, List<Throw> currentTurn,
                       List<Throw> lastTurn) {
    }

    private final RandomGenerator random;
    private final int[] scores = {START_SCORE, START_SCORE};
    private final int[] turnsTaken = {0, 0};
    private int turnSeat = 0;
    private int turnStartScore = START_SCORE;
    private List<Throw> currentTurn = new ArrayList<>();
    private List<Throw> lastTurn = List.of();
    private List<MatchResult> outcome;

    public DartsSession(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public List<Object> applyAction(int seat, JsonNode input) {
        requireTurn(seat);
        double aimX = Inputs.number(input, "x", -200, 200);
        double aimY = Inputs.number(input, "y", -200, 200);
        double release = Inputs.number(input, "release", 0, 1);

        double sigma = sigmaFor(release);
        double x = aimX + random.nextGaussian() * sigma;
        double y = aimY + random.nextGaussian() * sigma;
        DartBoard.Hit hit = DartBoard.score(x, y);

        int remaining = scores[seat] - hit.points();
        List<Object> events = new ArrayList<>();
        if (remaining < 0) {
            scores[seat] = turnStartScore;
            Throw t = new Throw(seat, aimX, aimY, round(x), round(y), hit.points(), hit.label(), true, scores[seat], false);
            currentTurn.add(t);
            events.add(t);
            events.add(endTurn(true));
            return events;
        }

        scores[seat] = remaining;
        boolean finished = remaining == 0;
        Throw t = new Throw(seat, aimX, aimY, round(x), round(y), hit.points(), hit.label(), false, remaining, finished);
        currentTurn.add(t);
        events.add(t);
        if (finished) {
            outcome = TwoPlayerOutcomes.winner(seat);
            lastTurn = List.copyOf(currentTurn);
            currentTurn = new ArrayList<>();
        } else if (currentTurn.size() == DARTS_PER_TURN) {
            events.add(endTurn(false));
        }
        return events;
    }

    @Override
    public List<Object> onTurnTimeout(int seat) {
        requireTurn(seat);
        List<Object> events = new ArrayList<>();
        while (currentTurn.size() < DARTS_PER_TURN) {
            Throw miss = new Throw(seat, 0, 0, 0, 0, 0, "MISS", false, scores[seat], false);
            currentTurn.add(miss);
            events.add(miss);
        }
        events.add(endTurn(false));
        return events;
    }

    private TurnEnded endTurn(boolean bust) {
        int seat = turnSeat;
        int scored = bust ? 0 : turnStartScore - scores[seat];
        lastTurn = List.copyOf(currentTurn);
        currentTurn = new ArrayList<>();
        turnsTaken[seat]++;
        if (turnsTaken[0] >= MAX_ROUNDS && turnsTaken[1] >= MAX_ROUNDS) {
            outcome = TwoPlayerOutcomes.lowerWins(scores[0], scores[1]);
        } else {
            turnSeat = 1 - seat;
            turnStartScore = scores[turnSeat];
        }
        return new TurnEnded(seat, scored, bust);
    }

    /** Scatter grows as the release strays from the 0.45-0.55 sweet spot. */
    static double sigmaFor(double release) {
        double error = Math.max(0, Math.abs(release - 0.5) - 0.05) / 0.45;
        return BASE_SIGMA + MAX_EXTRA_SIGMA * Math.min(1, error);
    }

    private void requireTurn(int seat) {
        if (outcome != null) throw InvalidActionException.gameOver();
        if (seat != turnSeat) throw InvalidActionException.notYourTurn();
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    @Override
    public Object view(int viewerSeat) {
        int round = Math.min(MAX_ROUNDS, Math.min(turnsTaken[0], turnsTaken[1]) + 1);
        return new View(scores.clone(), round, MAX_ROUNDS, turnSeat(), DARTS_PER_TURN - currentTurn.size(),
            List.copyOf(currentTurn), lastTurn);
    }

    @Override
    public int turnSeat() {
        return outcome == null ? turnSeat : -1;
    }

    @Override
    public Optional<List<MatchResult>> outcome() {
        return Optional.ofNullable(outcome);
    }
}
