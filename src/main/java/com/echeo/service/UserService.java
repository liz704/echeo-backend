package com.echeo.service;

import com.echeo.exception.EntityNotFoundException;
import com.echeo.util.ContactValidation;
import com.echeo.model.entity.User;
import com.echeo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gère la lecture et la mise à jour du profil de l'utilisateur courant.
 * Ne gère PAS le mot de passe (voir AuthService.changePassword) ni l'email
 * (changer l'email impacte l'identifiant de connexion — volontairement
 * exclu de cette version pour éviter les problèmes d'unicité/de session).
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getProfile(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User", userId));
    }

    @Transactional
    public User updateProfile(Long userId, String fullName, String phone, String preferredLocale) {
        User user = getProfile(userId);
        user.setFullName(fullName);
        user.setPhone(ContactValidation.normalizeAndValidatePhone(phone));
        if (preferredLocale != null) {
            String loc = preferredLocale.trim().toLowerCase();
            if (loc.equals("en") || loc.equals("fr")) {
                user.setPreferredLocale(loc);
            }
        }
        return userRepository.save(user);
    }
}
