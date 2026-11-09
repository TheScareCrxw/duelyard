package com.duelyard.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.duelyard.auth.CognitoUserInfoClient;

/**
 * Full application context backed by a real, embedded PostgreSQL. Cognito's user-info API is
 * mocked (inject {@link CognitoUserInfoClient} to stub it); every integration test shares this
 * single context.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import(EmbeddedPostgresTestConfiguration.class)
@MockitoBean(types = CognitoUserInfoClient.class)
public @interface IntegrationTest {
}
