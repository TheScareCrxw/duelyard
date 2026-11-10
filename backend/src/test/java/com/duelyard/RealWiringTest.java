package com.duelyard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.duelyard.auth.CognitoUserInfoClient;
import com.duelyard.support.EmbeddedPostgresTestConfiguration;

/**
 * Boots the application with every production bean, including ones other integration tests
 * replace with mocks (the Cognito client), so a wiring mistake can't hide behind a mock.
 */
@SpringBootTest
@Import(EmbeddedPostgresTestConfiguration.class)
class RealWiringTest {

    @Autowired
    CognitoUserInfoClient cognito;

    @Test
    void productionCognitoClientIsWired() {
        assertThat(cognito.getClass().getSimpleName()).isEqualTo("HttpCognitoUserInfoClient");
    }
}
