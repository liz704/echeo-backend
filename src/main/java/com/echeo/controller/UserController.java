package com.echeo.controller;

import com.echeo.dto.ChangePasswordRequest;
import com.echeo.dto.UpdateProfileRequest;
import com.echeo.dto.UserResponse;
import com.echeo.security.CustomUserDetails;
import com.echeo.service.AuthService;
import com.echeo.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Page "Paramètres" côté frontend : consultation et mise à jour du profil,
 * changement de mot de passe. Toutes les routes nécessitent un JWT valide
 * (non listées dans /api/v1/public/** ni /api/v1/auth/**).
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    public UserController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyProfile(@AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(UserResponse.from(userService.getProfile(currentUser.getUserId())));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request,
                                                          @AuthenticationPrincipal CustomUserDetails currentUser) {
        var updated = userService.updateProfile(currentUser.getUserId(), request.getFullName(), request.getPhone());
        return ResponseEntity.ok(UserResponse.from(updated));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                                @AuthenticationPrincipal CustomUserDetails currentUser) {
        authService.changePassword(currentUser.getUserId(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Mot de passe mis à jour avec succès."));
    }
}
