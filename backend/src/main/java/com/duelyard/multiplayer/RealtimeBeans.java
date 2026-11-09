package com.duelyard.multiplayer;

import java.security.SecureRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(RealtimeProperties.class)
class RealtimeBeans {

    /** Timers for countdowns, turn limits and disconnect grace periods. */
    @Bean(destroyMethod = "shutdownNow")
    ScheduledExecutorService realtimeScheduler() {
        return Executors.newScheduledThreadPool(2, Thread.ofVirtual().name("realtime-", 0).factory());
    }

    /** Each match gets its own generator, seeded from SecureRandom. */
    @Bean
    MatchRandomFactory matchRandomFactory() {
        SecureRandom seeds = new SecureRandom();
        RandomGeneratorFactory<RandomGenerator> factory = RandomGeneratorFactory.of("L64X128MixRandom");
        return () -> factory.create(seeds.nextLong());
    }
}
