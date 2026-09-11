package com.echeo.controller;

import com.echeo.dto.StatsResponse;
import com.echeo.service.StatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Statistiques globales de la plateforme. Route authentifiée (JWT requis) ;
 * si tu veux la réserver aux ADMIN uniquement, ajoute
 * @PreAuthorize("hasRole('ADMIN')") sur getGlobalStats() (nécessite
 * @EnableMethodSecurity sur SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public ResponseEntity<StatsResponse> getGlobalStats() {
        return ResponseEntity.ok(statsService.getGlobalStats());
    }
}
