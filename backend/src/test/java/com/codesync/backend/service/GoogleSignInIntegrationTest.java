package com.codesync.backend.service;

import com.codesync.backend.dto.LoginRequest;
import com.codesync.backend.entity.User;
import com.codesync.backend.exception.DuplicateUserException;
import com.codesync.backend.exception.InvalidCredentialsException;
import com.codesync.backend.exception.InvalidGoogleTokenException;
import com.codesync.backend.repository.UserRepository;
import com.codesync.backend.security.GoogleAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against the real database; every test is rolled back. Token checks are in GoogleTokenVerifierTest. */
@SpringBootTest(properties = "codesync.jwt.secret=test-only-secret-that-is-long-enough-for-hs256")
@Transactional
class GoogleSignInIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String suffix;

    @BeforeEach
    void pickSuffix() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
    }

    private GoogleAccount google(String email) {
        return new GoogleAccount("google-sub-" + suffix, email, true, "Ada Lovelace");
    }

    @Test
    void firstSignInCreatesAnAccountNamedAfterTheEmail() {
        User user = authService.loginWithGoogle(google("Ada.Lovelace+" + suffix + "@Gmail.com"));

        assertEquals("ada_lovelace_" + suffix, user.getUsername());
        assertEquals("ada.lovelace+" + suffix + "@gmail.com", user.getEmail());
        assertEquals("google-sub-" + suffix, user.getGoogleSubject());
        assertFalse(user.hasPassword());
    }

    @Test
    void laterSignInsFindTheSameAccountEvenIfTheEmailChanged() {
        User first = authService.loginWithGoogle(google("ada" + suffix + "@gmail.com"));
        User again = authService.loginWithGoogle(google("renamed" + suffix + "@gmail.com"));
        assertEquals(first.getId(), again.getId());
    }

    @Test
    void aTakenUsernameGetsANumber() {
        String base = "taken_" + suffix;
        userRepository.save(new User(base, "someone-" + suffix + "@example.com", "x"));

        String username = authService.loginWithGoogle(google("Taken." + suffix + "@gmail.com")).getUsername();
        assertTrue(username.matches(base + "_\\d{4}"), username);
    }

    @Test
    void shortNamesArePadded() {
        // A number is added if someone already has the name in this database
        assertTrue(authService.uniqueUsername("ab@example.com").matches("user_ab(_\\d{4})?"));
        assertTrue(authService.uniqueUsername("__@example.com").matches("user(_\\d{4})?"));
    }

    @Test
    void anExistingPasswordAccountIsNotTakenOver() {
        String email = "victim-" + suffix + "@gmail.com";
        userRepository.save(new User("pw-" + suffix, email, passwordEncoder.encode("password123")));

        assertThrows(DuplicateUserException.class, () -> authService.loginWithGoogle(google(email.toUpperCase())));
        assertTrue(userRepository.findByGoogleSubject("google-sub-" + suffix).isEmpty());
    }

    @Test
    void anUnverifiedEmailIsRefused() {
        GoogleAccount unverified = new GoogleAccount("google-sub-" + suffix, "u" + suffix + "@gmail.com", false, null);
        assertThrows(InvalidGoogleTokenException.class, () -> authService.loginWithGoogle(unverified));
    }

    @Test
    void passwordLoginToAGoogleAccountExplainsWhy() {
        String email = "g" + suffix + "@gmail.com";
        authService.loginWithGoogle(google(email));

        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword("");
        InvalidCredentialsException error = assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
        assertEquals("This account signs in with Google", error.getMessage());
    }
}
