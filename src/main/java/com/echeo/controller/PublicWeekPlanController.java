package com.echeo.controller;

import com.echeo.dto.WeekPlanSubmitRequest;
import com.echeo.model.entity.PersonalReminder;
import com.echeo.model.entity.User;
import com.echeo.service.WeekPlanService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/week-plan")
public class PublicWeekPlanController {

    private final WeekPlanService weekPlanService;

    public PublicWeekPlanController(WeekPlanService weekPlanService) {
        this.weekPlanService = weekPlanService;
    }

    @GetMapping("/{token}")
    public ResponseEntity<Map<String, String>> preview(@PathVariable UUID token) {
        User user = weekPlanService.getByToken(token);
        return ResponseEntity.ok(Map.of(
                "fullName", user.getFullName(),
                "locale", user.getPreferredLocale() != null ? user.getPreferredLocale() : "fr"
        ));
    }

    @PostMapping("/{token}")
    public ResponseEntity<Map<String, Object>> submit(@PathVariable UUID token,
                                                       @Valid @RequestBody WeekPlanSubmitRequest request) {
        List<PersonalReminder> created = weekPlanService.submitTasks(token, request);
        return ResponseEntity.ok(Map.of(
                "created", created.size(),
                "message", "OK"
        ));
    }
}
