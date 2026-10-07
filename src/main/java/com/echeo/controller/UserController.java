package com.echeo.controller;

import com.echeo.dto.ChangePasswordRequest;
import com.echeo.dto.UpdateProfileRequest;
import com.echeo.dto.UserResponse;
import com.echeo.security.CustomUserDetails;
import com.echeo.service.AuthService;
import com.echeo.service.UserService;
import com.echeo.service.WeekPlanService;
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
    private final WeekPlanService weekPlanService;

    public UserController(UserService userService, AuthService authService, WeekPlanService weekPlanService) {
        this.userService = userService;
        this.authService = authService;
        this.weekPlanService = weekPlanService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyProfile(@AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(UserResponse.from(userService.getProfile(currentUser.getUserId())));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request,
                                                          @AuthenticationPrincipal CustomUserDetails currentUser) {
        var updated = userService.updateProfile(currentUser.getUserId(), request.getFullName(), request.getPhone(), request.getPreferredLocale());
        return ResponseEntity.ok(UserResponse.from(updated));
    }

    @PutMapping("/me/week-plan")
    public ResponseEntity<UserResponse> updateWeekPlan(@RequestBody Map<String, Object> body,
                                                        @AuthenticationPrincipal CustomUserDetails currentUser) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("enabled")));
        int day = 1;
        if (body.get("day") instanceof Number n) {
            day = n.intValue();
        }
        java.time.LocalTime sendTime = java.time.LocalTime.of(8, 0);
        if (body.get("sendTime") != null) {
            sendTime = java.time.LocalTime.parse(String.valueOf(body.get("sendTime")));
        }
        var updated = weekPlanService.updateWeekPlanSettings(currentUser.getUserId(), enabled, day, sendTime);
        return ResponseEntity.ok(UserResponse.from(updated));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                                @AuthenticationPrincipal CustomUserDetails currentUser) {
        authService.changePassword(currentUser.getUserId(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Mot de passe mis à jour avec succès."));
    }
}
