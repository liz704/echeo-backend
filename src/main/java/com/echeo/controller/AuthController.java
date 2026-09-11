package com.echeo.controller;

import com.echeo.dto.AuthResponse;
import com.echeo.dto.ForgotPasswordRequest;
import com.echeo.dto.LoginRequest;
import com.echeo.dto.RegisterRequest;
import com.echeo.dto.ResetPasswordRequest;
import com.echeo.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints publics d'authentification : inscription, connexion (émission JWT),
 * et flux "mot de passe oublié". Aucune de ces routes ne requiert de token
 * (voir SecurityConfig : /api/v1/auth/** est en permitAll).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        // Réponse volontairement neutre : ne confirme pas si l'email existe réellement,
        // pour ne pas permettre l'énumération de comptes.
        return ResponseEntity.ok(Map.of("message",
                "Si un compte existe pour cet email, un lien de réinitialisation a été envoyé."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Mot de passe réinitialisé avec succès."));
    }
}
