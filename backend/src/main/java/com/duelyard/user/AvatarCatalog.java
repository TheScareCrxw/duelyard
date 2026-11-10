package com.duelyard.user;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preset avatars players can choose from, stored in {@code users.avatar_url} as
 * {@code preset:<emblem>:<color>}. The frontend renders them; this class only decides which
 * values are allowed. Uploaded images (S3) can later use the same column with an https URL.
 */
public final class AvatarCatalog {

    public static final List<String> EMBLEMS =
        List.of("bolt", "crown", "flame", "orbit", "shield", "star", "target", "wave");
    public static final List<String> COLORS =
        List.of("volt", "ember", "ocean", "jade", "amber", "violet", "rose", "slate");

    private static final Pattern PRESET = Pattern.compile("^preset:([a-z]+):([a-z]+)$");

    private AvatarCatalog() {
    }

    public static boolean isValid(String avatar) {
        if (avatar == null) {
            return false;
        }
        Matcher m = PRESET.matcher(avatar);
        return m.matches() && EMBLEMS.contains(m.group(1)) && COLORS.contains(m.group(2));
    }
}
