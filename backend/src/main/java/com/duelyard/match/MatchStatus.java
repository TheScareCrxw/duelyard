package com.duelyard.match;

/** Lifecycle of a match: PENDING, then IN_PROGRESS, then exactly one terminal state. */
public enum MatchStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    ABANDONED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == ABANDONED || this == CANCELLED;
    }
}
