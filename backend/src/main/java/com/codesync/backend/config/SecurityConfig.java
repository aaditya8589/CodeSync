package com.codesync.backend.config;

import com.codesync.backend.security.JwtAuthenticationFilter;
import com.codesync.backend.security.JwtService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Logins go through AuthService and JWTs, never through Spring's username/password login.
    // Without a UserDetailsService bean, Spring Boot creates a default user with a generated
    // password and prints it at startup; this bean turns that off.
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Password login is not used; sign in through /api/auth/login");
        };
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtService jwtService
    ) {
        return new JwtAuthenticationFilter(jwtService);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${codesync.cors.allowed-origins}") String[] allowedOrigins
    ) {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(trimmed(allowedOrigins));

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type"
                )
        );

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }

    // "a, b" in the environment variable should mean "a" and "b"
    static List<String> trimmed(String[] values) {
        return java.util.Arrays.stream(values).map(String::trim).filter(value -> !value.isEmpty()).toList();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {

        http
            .csrf(AbstractHttpConfigurer::disable)

            .cors(cors -> {})

            .sessionManagement(session ->
                    session.sessionCreationPolicy(
                            SessionCreationPolicy.STATELESS
                    )
            )

            .authorizeHttpRequests(auth -> auth

                .requestMatchers(
                        HttpMethod.OPTIONS,
                        "/**"
                ).permitAll()

                .requestMatchers(
        "/api/auth/register",
        "/api/auth/login",
        "/ws/**",
        "/error"
).permitAll()

                .anyRequest().authenticated()
            )

            .addFilterBefore(
                    jwtAuthenticationFilter,
                    UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}