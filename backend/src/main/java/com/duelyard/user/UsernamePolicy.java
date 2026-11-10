package com.duelyard.user;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * Username format rules (mirrored by a database CHECK constraint) and generation of valid
 * usernames for accounts whose Cognito username can't be used directly, e.g. Google sign-ins.
 */
public final class UsernamePolicy {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 20;
    public static final String PATTERN = "^[A-Za-z0-9_]{3,20}$";

    private static final Pattern VALID = Pattern.compile(PATTERN);
    private static final Pattern INVALID_CHARS = Pattern.compile("[^A-Za-z0-9_]");
    private static final int SUFFIX_DIGITS = 4;

    /** Names that could be mistaken for staff or system accounts. Compared case-insensitively. */
    private static final Set<String> RESERVED = Set.of(
        "admin", "administrator", "root", "system", "support", "help", "staff", "moderator", "mod",
        "duelyard", "official", "security", "null", "undefined", "anonymous", "deleted", "me", "settings");

    private final RandomGenerator random;

    public UsernamePolicy() {
        this(new SecureRandom());
    }

    UsernamePolicy(RandomGenerator random) {
        this.random = random;
    }

    public static boolean isValid(String username) {
        return username != null && VALID.matcher(username).matches();
    }

    public static boolean isReserved(String username) {
        return username != null && RESERVED.contains(username.toLowerCase(Locale.ROOT));
    }

    /** Valid format and not reserved: acceptable as a player's username. */
    public static boolean isAllowed(String username) {
        return isValid(username) && !isReserved(username);
    }

    /** Turns arbitrary text (e.g. an email local part) into a valid base username. */
    public static String sanitize(String raw) {
        String cleaned = INVALID_CHARS.matcher(raw == null ? "" : raw).replaceAll("_");
        cleaned = cleaned.replaceAll("_{2,}", "_").replaceAll("^_|_$", "");
        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH);
        }
        if (cleaned.length() < MIN_LENGTH || isReserved(cleaned)) {
            cleaned = "player";
        }
        return cleaned;
    }

    /** Base name plus a random numeric suffix, truncated so the result stays within the limit. */
    public String withRandomSuffix(String base) {
        String suffix = String.format(Locale.ROOT, "%0" + SUFFIX_DIGITS + "d", random.nextInt(10_000));
        String head = base.length() > MAX_LENGTH - SUFFIX_DIGITS ? base.substring(0, MAX_LENGTH - SUFFIX_DIGITS) : base;
        return head + suffix;
    }
}
