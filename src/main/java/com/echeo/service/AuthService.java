package com.echeo.service;

import com.echeo.dto.AuthResponse;
import com.echeo.dto.LoginRequest;
import com.echeo.dto.RegisterRequest;
import com.echeo.exception.EntityNotFoundException;
import com.echeo.exception.InvalidArgumentException;
import com.echeo.model.entity.PasswordResetToken;
import com.echeo.model.entity.User;
import com.echeo.model.enums.Role;
import com.echeo.repository.PasswordResetTokenRepository;
import com.echeo.repository.UserRepository;
import com.echeo.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Logique métier d'authentification : inscription, connexion (émission JWT),
 * et flux complet "mot de passe oublié" (génération + consommation de token).
 */
@Service
public class AuthService {

    private static final Duration RESET_TOKEN_VALIDITY = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final EmailService emailService;

    @Value("${echeo.frontend.base-url}")
    private String frontendBaseUrl;

    public AuthService(UserRepository userRepository,
                        PasswordResetTokenRepository passwordResetTokenRepository,
                        PasswordEncoder passwordEncoder,
                        AuthenticationManager authenticationManager,
                        JwtService jwtService,
                        EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.emailService = emailService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(existing -> {
            throw new InvalidArgumentException("Un compte existe déjà avec cet email.");
        });

        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);

        User saved = userRepository.save(user);
        String token = jwtService.generateToken(saved);

        return new AuthResponse(token, saved.getId(), saved.getFullName(), saved.getEmail(), saved.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        // authenticate() lève une AuthenticationException (401 via le GlobalExceptionHandler)
        // si l'email est inconnu ou le mot de passe incorrect.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("User introuvable pour l'email : " + request.getEmail()));

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getId(), user.getFullName(), user.getEmail(), user.getRole().name());
    }

    /**
     * Génère un token de réinitialisation et déclenche l'envoi de l'email
     * (simulé ici — à brancher sur le provider d'emailing réel en production).
     */
    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User introuvable pour l'email : " + email));

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setTokenUuid(UUID.randomUUID());
        resetToken.setUser(user);
        resetToken.setExpiresAt(OffsetDateTime.now().plus(RESET_TOKEN_VALIDITY));
        resetToken.setUsed(false);
        passwordResetTokenRepository.save(resetToken);

        sendResetEmail(user.getEmail(), resetToken.getTokenUuid());
    }

    /**
     * Consomme un token de réinitialisation valide et applique le nouveau mot de passe.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        UUID tokenUuid;
        try {
            tokenUuid = UUID.fromString(rawToken);
        } catch (IllegalArgumentException ex) {
            throw new InvalidArgumentException("Format de token invalide.");
        }

        PasswordResetToken token = passwordResetTokenRepository.findByTokenUuid(tokenUuid)
                .orElseThrow(() -> new EntityNotFoundException("Token de réinitialisation introuvable."));

        if (token.isUsed()) {
            throw new InvalidArgumentException("Ce lien de réinitialisation a déjà été utilisé.");
        }
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidArgumentException("Ce lien de réinitialisation a expiré.");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setUsed(true);
        passwordResetTokenRepository.save(token);
    }

    /**
     * Change le mot de passe de l'utilisateur courant après vérification
     * du mot de passe actuel (empêche quiconque a un token volé mais pas
     * le mot de passe de le changer silencieusement).
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User", userId));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidArgumentException("Le mot de passe actuel est incorrect.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * Point d'intégration avec le provider d'emailing réel : envoi effectif
     * via SMTP (voir EmailService, configuré dans application.yml).
     */
    private void sendResetEmail(String email, UUID tokenUuid) {
        String resetLink = frontendBaseUrl + "/reset-password?token=" + tokenUuid;
        String body = "Bonjour,\n\n"
                + "Une réinitialisation de mot de passe a été demandée pour votre compte ÉCHÉO.\n"
                + "Cliquez sur ce lien pour choisir un nouveau mot de passe (valable 1 heure) :\n\n"
                + resetLink + "\n\n"
                + "Si vous n'êtes pas à l'origine de cette demande, ignorez simplement cet email.";

        emailService.send(email, "ÉCHÉO — Réinitialisation de votre mot de passe", body);
    }
}
