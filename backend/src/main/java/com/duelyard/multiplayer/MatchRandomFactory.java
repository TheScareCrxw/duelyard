package com.duelyard.multiplayer;

import java.util.random.RandomGenerator;

/** Source of each match's randomness. Production seeds from SecureRandom; tests can fix the seed. */
@FunctionalInterface
public interface MatchRandomFactory {

    RandomGenerator create();
}
