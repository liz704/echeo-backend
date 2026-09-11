package com.echeo.controller;

import com.echeo.dto.AddGroupMemberRequest;
import com.echeo.dto.GroupCreateRequest;
import com.echeo.dto.GroupEventRequest;
import com.echeo.dto.GroupEventUpdateRequest;
import com.echeo.model.entity.EventMemberStatus;
import com.echeo.model.entity.Group;
import com.echeo.model.entity.GroupEvent;
import com.echeo.model.entity.GroupMember;
import com.echeo.security.CustomUserDetails;
import com.echeo.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Gestion des groupes, de leurs membres, et des événements de cotisation associés.
 * Toutes les routes nécessitent un JWT valide (aucune n'est dans /api/v1/public/**).
 */
@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    public ResponseEntity<Group> createGroup(@Valid @RequestBody GroupCreateRequest request,
                                              @AuthenticationPrincipal CustomUserDetails currentUser) {
        Group group = groupService.createGroup(request.getName(), request.getDescription(), currentUser.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(group);
    }

    /**
     * Tous les groupes de l'utilisateur courant (possédés ou dont il est
     * membre). Point d'entrée de la page frontend /groups.
     */
    @GetMapping("/me")
    public ResponseEntity<List<Group>> myGroups(@AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(groupService.listMyGroups(currentUser.getUserId()));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<Group> getGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(groupService.getGroup(groupId));
    }

    @PostMapping("/{groupId}/members")
    public ResponseEntity<GroupMember> addMember(@PathVariable Long groupId,
                                                  @Valid @RequestBody AddGroupMemberRequest request) {
        GroupMember member = groupService.addMember(groupId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(member);
    }

    /**
     * Liste des membres du groupe (inscrits et externes confondus) — utilisé
     * par le frontend pour construire le sélecteur de membres à l'écran de
     * création d'événement.
     */
    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<GroupMember>> listMembers(@PathVariable Long groupId) {
        return ResponseEntity.ok(groupService.listMembers(groupId));
    }

    @PostMapping("/{groupId}/events")
    public ResponseEntity<GroupEvent> createEvent(@PathVariable Long groupId,
                                                   @Valid @RequestBody GroupEventRequest request) {
        GroupEvent event = groupService.createEvent(groupId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }

    @GetMapping("/{groupId}/events")
    public ResponseEntity<List<GroupEvent>> listEvents(@PathVariable Long groupId) {
        return ResponseEntity.ok(groupService.listEvents(groupId));
    }

    @GetMapping("/events/{eventId}/statuses")
    public ResponseEntity<List<EventMemberStatus>> listMemberStatuses(@PathVariable Long eventId) {
        return ResponseEntity.ok(groupService.listMemberStatuses(eventId));
    }

    /**
     * Modifie un événement qui n'a pas encore eu lieu (titre, description,
     * montant, date, récurrence). Ne touche pas aux membres concernés.
     */
    @PutMapping("/{groupId}/events/{eventId}")
    public ResponseEntity<GroupEvent> updateEvent(@PathVariable Long groupId, @PathVariable Long eventId,
                                                   @Valid @RequestBody GroupEventUpdateRequest request) {
        return ResponseEntity.ok(groupService.updateUpcomingEvent(groupId, eventId, request));
    }

    /**
     * Suspend la reconduction automatique d'un événement récurrent, sans le
     * supprimer ni toucher aux occurrences déjà générées.
     */
    @PatchMapping("/{groupId}/events/{eventId}/pause")
    public ResponseEntity<GroupEvent> pauseEvent(@PathVariable Long groupId, @PathVariable Long eventId) {
        return ResponseEntity.ok(groupService.pauseEvent(groupId, eventId));
    }

    /**
     * Reprend la reconduction automatique d'un événement récurrent mis en pause.
     */
    @PatchMapping("/{groupId}/events/{eventId}/resume")
    public ResponseEntity<GroupEvent> resumeEvent(@PathVariable Long groupId, @PathVariable Long eventId) {
        return ResponseEntity.ok(groupService.resumeEvent(groupId, eventId));
    }
}
