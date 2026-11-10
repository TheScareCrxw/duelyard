package com.duelyard.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AvatarCatalogTest {

    @Test
    void acceptsOnlyKnownPresets() {
        assertThat(AvatarCatalog.isValid("preset:bolt:volt")).isTrue();
        assertThat(AvatarCatalog.isValid("preset:wave:slate")).isTrue();
        assertThat(AvatarCatalog.isValid("preset:bolt:neon")).isFalse();
        assertThat(AvatarCatalog.isValid("preset:skull:volt")).isFalse();
        assertThat(AvatarCatalog.isValid("https://example.com/a.png")).isFalse();
        assertThat(AvatarCatalog.isValid("preset:bolt:volt:extra")).isFalse();
        assertThat(AvatarCatalog.isValid(null)).isFalse();
    }

    @Test
    void reservedNamesAreCaseInsensitive() {
        assertThat(UsernamePolicy.isReserved("ADMIN")).isTrue();
        assertThat(UsernamePolicy.isAllowed("Duelyard")).isFalse();
        assertThat(UsernamePolicy.isAllowed("admin_ace")).isTrue();
        assertThat(UsernamePolicy.sanitize("support")).isEqualTo("player");
    }
}
