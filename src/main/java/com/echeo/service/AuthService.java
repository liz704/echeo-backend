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
import com.echeo.util.ContactValidation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Duration RESET_TOKEN_VALIDITY = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final EmailService emailService;

    @Value("${echeo.frontend.base-url:https://echeo-one.vercel.app}")
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
        ContactValidation.validateEmailFormat(request.getEmail());
        String phone = ContactValidation.normalizeAndValidatePhone(request.getPhone());

        userRepository.findByEmail(request.getEmail().trim().toLowerCase()).ifPresent(existing -> {
            throw new InvalidArgumentException("Un compte existe déjà avec cet email.");
        });

        User user = new User();
        user.setFullName(request.getFullName().trim());
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);
        user.setEmailVerified(false);
        user.setEmailVerificationToken(UUID.randomUUID());
        user.setEmailVerificationSentAt(OffsetDateTime.now());

        User saved = userRepository.save(user);
        sendVerificationEmail(saved);

        String token = jwtService.generateToken(saved);
        return new AuthResponse(token, saved.getId(), saved.getFullName(), saved.getEmail(), saved.getRole().name());
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        UUID tokenUuid;
        try {
            tokenUuid = UUID.fromString(rawToken);
        } catch (IllegalArgumentException ex) {
            throw new InvalidArgumentException("Lien de confirmation invalide.");
        }
        User user = userRepository.findByEmailVerificationToken(tokenUuid)
                .orElseThrow(() -> new InvalidArgumentException("Lien de confirmation invalide ou déjà utilisé."));
        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        userRepository.save(user);
    }

    private void sendVerificationEmail(User user) {
        String base = frontendBaseUrl == null ? "https://echeo-one.vercel.app"
                : frontendBaseUrl.replaceAll("/+$", "");
        String link = base + "/verify-email?token=" + user.getEmailVerificationToken();
        String subject = "ÉCHÉO — Confirme ton email";
        String body = "Bonjour " + user.getFullName() + ",\n\n"
                + "Confirme ton adresse email en ouvrant ce lien :\n" + link + "\n\n"
                + "Si tu n'as pas créé de compte ÉCHÉO, ignore cet email.";
        try {
            emailService.send(user.getEmail(), subject, body);
        } catch (Exception ex) {
            log.warn("Échec envoi email de confirmation à {} : {}", user.getEmail(), ex.getMessage());
        }
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
     * via Brevo (voir EmailService). L'échec d'envoi (ex. clé API Brevo
     * absente/invalide) est capturé ici pour ne PAS faire planter toute la
     * requête HTTP — le token est déjà créé en base à ce stade, un échec
     * d'email ne doit pas empêcher l'utilisateur de réessayer proprement.
     */
    private void sendResetEmail(String email, UUID tokenUuid) {
        String resetLink = frontendBaseUrl + "/reset-password?token=" + tokenUuid;
        String body = "Bonjour,\n\n"
                + "Une réinitialisation de mot de passe a été demandée pour votre compte ÉCHÉO.\n"
                + "Cliquez sur ce lien pour choisir un nouveau mot de passe (valable 1 heure) :\n\n"
                + resetLink + "\n\n"
                + "Si vous n'êtes pas à l'origine de cette demande, ignorez simplement cet email.";

        try {
            String messageId = emailService.send(email, "ÉCHÉO — Réinitialisation de votre mot de passe", body);
            log.info("Email de réinitialisation accepté par Brevo pour {} (messageId={})", email, messageId);
        } catch (Exception ex) {
            // Stack complète + message pour diagnostiquer (clé API, sender non vérifié, etc.)
            log.error("Échec de l'envoi de l'email de réinitialisation à {} : {}", email, ex.getMessage(), ex);
        }
    }
}
