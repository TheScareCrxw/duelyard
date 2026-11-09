package com.duelyard.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;

import org.junit.jupiter.api.Test;

class UsernamePolicyTest {

    @Test
    void validatesFormat() {
        assertThat(UsernamePolicy.isValid("Ace_99")).isTrue();
        assertThat(UsernamePolicy.isValid("ab")).isFalse();
        assertThat(UsernamePolicy.isValid("a".repeat(21))).isFalse();
        assertThat(UsernamePolicy.isValid("has space")).isFalse();
        assertThat(UsernamePolicy.isValid(null)).isFalse();
    }

    @Test
    void sanitizesEmailLocalParts() {
        assertThat(UsernamePolicy.sanitize("john.doe+games")).isEqualTo("john_doe_games");
        assertThat(UsernamePolicy.sanitize("..x..")).isEqualTo("player");
        assertThat(UsernamePolicy.sanitize("averyveryverylongemailname")).hasSize(20);
        assertThat(UsernamePolicy.isValid(UsernamePolicy.sanitize("ünïcödé.name"))).isTrue();
    }

    @Test
    void randomSuffixKeepsUsernameValid() {
        UsernamePolicy policy = new UsernamePolicy(new Random(1));
        String generated = policy.withRandomSuffix("abcdefghijklmnopqrst");
        assertThat(generated).hasSize(20).matches(UsernamePolicy.PATTERN).startsWith("abcdefghijklmnop");
        assertThat(policy.withRandomSuffix("ace")).matches("ace[0-9]{4}");
    }
}
