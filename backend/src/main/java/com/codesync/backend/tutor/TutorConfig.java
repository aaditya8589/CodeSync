package com.codesync.backend.tutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * The tutor's provider and limits, from settings. Without GEMINI_API_KEY the tutor is simply
 * off: the rest of CodeSync works the same and the UI says so.
 */
@Configuration
public class TutorConfig {

    @Bean
    public TutorProvider tutorProvider(
            @Value("${codesync.tutor.gemini.api-key:}") String apiKey,
            @Value("${codesync.tutor.gemini.model:gemini-flash-lite-latest}") String model,
            @Value("${codesync.tutor.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl
    ) {
        return new GeminiProvider(baseUrl, apiKey, model, Duration.ofSeconds(60));
    }

    @Bean
    public TutorRateLimiter tutorRateLimiter(
            @Value("${codesync.tutor.limit.per-hour:15}") int perHour,
            @Value("${codesync.tutor.limit.per-day:50}") int perDay,
            @Value("${codesync.tutor.limit.server-per-day:400}") int serverPerDay
    ) {
        return new TutorRateLimiter(perHour, perDay, serverPerDay, Clock.systemUTC());
    }
}
