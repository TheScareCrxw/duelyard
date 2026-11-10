package com.duelyard.game.engine;

import tools.jackson.databind.JsonNode;

/** Strict readers for client input. Anything missing, non-numeric, non-finite or out of range is rejected. */
public final class Inputs {

    private Inputs() {
    }

    public static double number(JsonNode input, String field, double min, double max) {
        if (input == null || !input.isObject()) {
            throw InvalidActionException.badInput("Input must be an object.");
        }
        JsonNode node = input.get(field);
        if (node == null || !node.isNumber()) {
            throw InvalidActionException.badInput("'" + field + "' must be a number.");
        }
        double value = node.asDouble();
        if (!Double.isFinite(value) || value < min || value > max) {
            throw InvalidActionException.badInput("'" + field + "' must be between " + min + " and " + max + ".");
        }
        return value;
    }
}
