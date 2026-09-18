package com.echeo.dto;

import com.echeo.model.enums.RepetitionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class GroupEventRequest {

    @NotBlank(message = "Le titre de l'événement est obligatoire.")
    private String title;

    private String description;

    // Optionnel : absent = événement "sans argent" (simple info diffusée au
    // groupe, suivie par accusé de lecture au lieu d'un suivi de paiement).
    @DecimalMin(value = "0.0", inclusive = true, message = "Le montant cible ne peut pas être négatif.")
    private BigDecimal targetAmount;

    @NotNull(message = "La date de l'événement est obligatoire.")
    private LocalDate eventDate;

    // Optionnelle : heure à laquelle les relances de cet événement doivent
    // être envoyées. Si absente, 08:00 est utilisé par défaut.
    private java.time.LocalTime eventTime;

    // NONE par défaut si non fourni — voir GroupService.
    private RepetitionType repetitionType;

    // Identifiants des GroupMember (pas des User — un membre peut ne pas
    // avoir de compte) pour lesquels un EventMemberStatus doit être créé
    // automatiquement (montant requis réparti également, ou personnalisable
    // via requiredAmountsByGroupMemberId).
    private List<Long> groupMemberIds;

    // Optionnel : montant requis spécifique par membre (groupMemberId -> montant).
    // Si absent pour un membre listé dans groupMemberIds, targetAmount / nombre de membres est utilisé.
    private Map<Long, BigDecimal> requiredAmountsByGroupMemberId;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public void setTargetAmount(BigDecimal targetAmount) {
        this.targetAmount = targetAmount;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public java.time.LocalTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(java.time.LocalTime eventTime) {
        this.eventTime = eventTime;
    }

    public List<Long> getGroupMemberIds() {
        return groupMemberIds;
    }

    public void setGroupMemberIds(List<Long> groupMemberIds) {
        this.groupMemberIds = groupMemberIds;
    }

    public Map<Long, BigDecimal> getRequiredAmountsByGroupMemberId() {
        return requiredAmountsByGroupMemberId;
    }

    public void setRequiredAmountsByGroupMemberId(Map<Long, BigDecimal> requiredAmountsByGroupMemberId) {
        this.requiredAmountsByGroupMemberId = requiredAmountsByGroupMemberId;
    }

    public RepetitionType getRepetitionType() {
        return repetitionType;
    }

    public void setRepetitionType(RepetitionType repetitionType) {
        this.repetitionType = repetitionType;
    }
}
