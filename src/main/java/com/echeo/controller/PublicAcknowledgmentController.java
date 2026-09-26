package com.echeo.controller;

import com.echeo.dto.PublicAcknowledgmentResponse;
import com.echeo.exception.InvalidArgumentException;
import com.echeo.model.entity.EventMemberStatus;
import com.echeo.service.GroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoint PUBLIC (aucun JWT requis — voir SecurityConfig : /api/v1/public/**
 * est en permitAll) pour le lien "marquer comme vu" des rappels de groupe
 * sans argent. Contrairement au paiement, un simple GET suffit : l'enjeu est
 * faible (juste un accusé de lecture), donc pas d'étape de confirmation
 * séparée — visiter le lien marque directement le rappel comme vu.
 */
@RestController
@RequestMapping("/api/v1/public/ack")
public class PublicAcknowledgmentController {

    private final GroupService groupService;

    public PublicAcknowledgmentController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping("/{token}")
    public ResponseEntity<PublicAcknowledgmentResponse> acknowledge(@PathVariable String token) {
        UUID tokenUuid;
        try {
            tokenUuid = UUID.fromString(token);
        } catch (IllegalArgumentException ex) {
            throw new InvalidArgumentException("Lien invalide.");
        }

        EventMemberStatus status = groupService.acknowledgeByToken(tokenUuid);

        PublicAcknowledgmentResponse response = new PublicAcknowledgmentResponse(
                status.getEvent().getTitle(),
                status.getGroupMember().getContactFullName(),
                status.getStatus(),
                status.getSeenAt()
        );
        return ResponseEntity.ok(response);
    }
}
