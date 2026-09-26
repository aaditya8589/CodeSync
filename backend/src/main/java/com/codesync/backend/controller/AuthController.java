package com.codesync.backend.controller;

import com.codesync.backend.dto.LoginRequest;
import com.codesync.backend.dto.LoginResponse;
import com.codesync.backend.dto.RegisterRequest;
import com.codesync.backend.dto.UserResponse;
import com.codesync.backend.entity.User;
import com.codesync.backend.service.AuthService;
import com.codesync.backend.security.JwtService;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(
        AuthService authService,
        JwtService jwtService
) {
    this.authService = authService;
    this.jwtService = jwtService;
}

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        User user = authService.register(request);

        UserResponse response = new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getCreatedAt()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

 @PostMapping("/login")
public ResponseEntity<LoginResponse> login(
        @Valid @RequestBody LoginRequest request
) {
    User user = authService.login(request);

    String token = jwtService.generateToken(
            user.getUsername()
    );

    return ResponseEntity.ok(
            new LoginResponse(token)
    );
}
}