package com.duelyard.user;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.duelyard.auth.CognitoUnavailableException;
import com.duelyard.auth.CognitoUserInfo;
import com.duelyard.auth.CognitoUserInfoClient;
import com.duelyard.game.GameRepository;
import com.duelyard.rating.PlayerGameStats;
import com.duelyard.rating.PlayerGameStatsRepository;

/**
 * Maps an authenticated Cognito identity to an application {@link User}, creating the account
 * (and a starting rating for every game) the first time an identity is seen.
 *
 * <p>Safe under concurrency: if two first requests for the same identity race, the unique
 * constraint on {@code cognito_sub} lets exactly one insert win and the other re-reads it. A
 * username taken concurrently by someone else is retried with a generated alternative.
 */
@Service
public class UserProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(UserProvisioningService.class);
    private static final int MAX_ATTEMPTS = 5;

    private final UserRepository users;
    private final GameRepository games;
    private final PlayerGameStatsRepository stats;
    private final CognitoUserInfoClient cognito;
    private final UsernamePolicy usernamePolicy;
    private final TransactionTemplate newTransaction;

    public UserProvisioningService(
            UserRepository users,
            GameRepository games,
            PlayerGameStatsRepository stats,
            CognitoUserInfoClient cognito,
            PlatformTransactionManager transactionManager) {
        this.users = users;
        this.games = games;
        this.stats = stats;
        this.cognito = cognito;
        this.usernamePolicy = new UsernamePolicy();
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public User getOrProvision(Jwt jwt) {
        String sub = jwt.getSubject();
        Optional<User> existing = users.findByCognitoSub(sub);
        if (existing.isPresent()) {
            return existing.get();
        }

        CognitoUserInfo info = cognito.fetch(jwt.getTokenValue());
        if (info.email() == null || info.email().isBlank()) {
            throw new CognitoUnavailableException("Cognito user " + sub + " has no email attribute", null);
        }

        String preferred = preferredUsername(info);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = attempt == 0 && !users.existsByUsernameIgnoreCase(preferred)
                ? preferred
                : usernamePolicy.withRandomSuffix(preferred);
            try {
                User created = newTransaction.execute(status -> create(sub, candidate, info.email()));
                log.info("Provisioned user id={} username={} federated={}", created.getId(), candidate, info.federated());
                return created;
            } catch (DataIntegrityViolationException e) {
                Optional<User> raced = users.findByCognitoSub(sub);
                if (raced.isPresent()) {
                    return raced.get();
                }
                // Otherwise the username was taken concurrently; try another.
            }
        }
        throw new IllegalStateException("Could not allocate a username for Cognito user " + sub);
    }

    private User create(String sub, String username, String email) {
        User user = users.saveAndFlush(new User(sub, username, email));
        games.findAll().forEach(game -> stats.save(new PlayerGameStats(user, game)));
        stats.flush();
        return user;
    }

    /**
     * Native accounts keep the username they registered with (the registration form enforces the
     * same rules). Federated accounts get one derived from their email address.
     */
    private static String preferredUsername(CognitoUserInfo info) {
        if (!info.federated() && UsernamePolicy.isValid(info.username())) {
            return info.username();
        }
        String localPart = info.email().substring(0, Math.max(0, info.email().indexOf('@')));
        return UsernamePolicy.sanitize(localPart);
    }
}
