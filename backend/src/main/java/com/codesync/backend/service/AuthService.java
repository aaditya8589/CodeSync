package com.codesync.backend.service;

import com.codesync.backend.dto.RegisterRequest;
import com.codesync.backend.entity.User;
import com.codesync.backend.exception.DuplicateUserException;
import com.codesync.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.codesync.backend.dto.LoginRequest;
import com.codesync.backend.exception.InvalidCredentialsException;
import com.codesync.backend.exception.InvalidGoogleTokenException;
import com.codesync.backend.security.GoogleAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Locale;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(RegisterRequest request) {

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateUserException("Username already exists");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateUserException("Email already exists");
        }

        String hashedPassword =
                passwordEncoder.encode(request.getPassword());

        User user = new User(
                request.getUsername(),
                request.getEmail(),
                hashedPassword
        );

        return userRepository.save(user);
    }

    public User login(LoginRequest request) {

    User user = userRepository
            .findByEmail(request.getEmail())
            .orElseThrow(() ->
                    new InvalidCredentialsException(
                            "Invalid email or password"
                    )
            );

    if (!user.hasPassword()) {
        throw new InvalidCredentialsException("This account signs in with Google");
    }

    boolean passwordMatches = passwordEncoder.matches(
            request.getPassword(),
            user.getPasswordHash()
    );

    if (!passwordMatches) {
        throw new InvalidCredentialsException(
                "Invalid email or password"
        );
    }

    return user;
}

    /**
     * Signs in the person a verified Google ID token belongs to, creating their account on the
     * first sign-in. Accounts are matched by Google's permanent id, not by email.
     *
     * An existing password account with the same email is not taken over: registration never
     * checks that an email belongs to the person typing it, so anyone could have registered
     * someone else's address with a password they know, and linking Google to it would let
     * them into that person's rooms.
     */
    @Transactional
    public User loginWithGoogle(GoogleAccount account) {
        var existing = userRepository.findByGoogleSubject(account.subject());
        if (existing.isPresent()) {
            return existing.get();
        }

        if (!account.emailVerified()) {
            throw new InvalidGoogleTokenException("Google has not verified this email address");
        }
        String email = account.email().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateUserException(
                    "An account with this email already exists. Sign in with your email and password.");
        }

        User user = userRepository.save(User.fromGoogle(uniqueUsername(email), email, account.subject()));
        log.info("Created account {} with Google sign-in", user.getUsername());
        return user;
    }

    /** Usernames are shown to the room, so they come from the email's name part, never the domain. */
    String uniqueUsername(String email) {
        String base = email.substring(0, Math.max(0, email.indexOf('@')))
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.length() > 30) base = base.substring(0, 30);
        if (base.length() < 3) base = "user" + (base.isEmpty() ? "" : "_" + base);

        if (!userRepository.existsByUsername(base)) return base;
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = base + "_" + (1000 + RANDOM.nextInt(9000));
            if (!userRepository.existsByUsername(candidate)) return candidate;
        }
        throw new IllegalStateException("Could not find a free username for " + base);
    }
}
