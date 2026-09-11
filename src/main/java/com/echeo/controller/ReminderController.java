package com.echeo.controller;

import com.echeo.dto.ReminderRequest;
import com.echeo.dto.ReminderResponse;
import com.echeo.model.entity.PersonalReminder;
import com.echeo.security.CustomUserDetails;
import com.echeo.service.ReminderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CRUD des rappels personnels, scopé strictement à l'utilisateur authentifié
 * (récupéré depuis le JWT via @AuthenticationPrincipal — voir JwtAuthenticationFilter).
 */
@RestController
@RequestMapping("/api/v1/reminders")
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @PostMapping
    public ResponseEntity<ReminderResponse> create(@Valid @RequestBody ReminderRequest request,
                                                     @AuthenticationPrincipal CustomUserDetails currentUser) {
        PersonalReminder reminder = new PersonalReminder();
        reminder.setUser(currentUser.getUser());
        reminder.setTitle(request.getTitle());
        reminder.setDescription(request.getDescription());
        reminder.setDueDate(request.getDueDate());
        reminder.setDueTime(request.getDueTime());
        reminder.setRepetitionType(request.getRepetitionType());

        PersonalReminder created = reminderService.createReminder(reminder);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReminderResponse.from(created));
    }

    @GetMapping
    public ResponseEntity<List<ReminderResponse>> listActive(@AuthenticationPrincipal CustomUserDetails currentUser) {
        List<ReminderResponse> reminders = reminderService.listActiveForUser(currentUser.getUserId()).stream()
                .map(ReminderResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(reminders);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReminderResponse> getOne(@PathVariable Long id,
                                                     @AuthenticationPrincipal CustomUserDetails currentUser) {
        PersonalReminder reminder = reminderService.getOwnedReminder(id, currentUser.getUserId());
        return ResponseEntity.ok(ReminderResponse.from(reminder));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReminderResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody ReminderRequest request,
                                                     @AuthenticationPrincipal CustomUserDetails currentUser) {
        // Vérifie la propriété avant modification (404 si le rappel n'appartient pas à l'utilisateur).
        reminderService.getOwnedReminder(id, currentUser.getUserId());

        PersonalReminder updatedData = new PersonalReminder();
        updatedData.setUser(currentUser.getUser());
        updatedData.setTitle(request.getTitle());
        updatedData.setDescription(request.getDescription());
        updatedData.setDueDate(request.getDueDate());
        updatedData.setDueTime(request.getDueTime());
        updatedData.setRepetitionType(request.getRepetitionType());

        PersonalReminder updated = reminderService.updateReminder(id, updatedData);
        return ResponseEntity.ok(ReminderResponse.from(updated));
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<ReminderResponse> complete(@PathVariable Long id,
                                                        @AuthenticationPrincipal CustomUserDetails currentUser) {
        reminderService.getOwnedReminder(id, currentUser.getUserId());
        PersonalReminder completed = reminderService.markAsCompleted(id);
        return ResponseEntity.ok(ReminderResponse.from(completed));
    }

    @GetMapping("/history")
    public ResponseEntity<List<ReminderResponse>> history(@AuthenticationPrincipal CustomUserDetails currentUser) {
        List<ReminderResponse> history = reminderService.listHistoryForUser(currentUser.getUserId()).stream()
                .map(ReminderResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(history);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                         @AuthenticationPrincipal CustomUserDetails currentUser) {
        reminderService.getOwnedReminder(id, currentUser.getUserId());
        reminderService.deleteReminder(id);
        return ResponseEntity.noContent().build();
    }
}
