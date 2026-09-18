package com.echeo.dto;

import com.echeo.model.enums.RepetitionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Modification d'un événement de groupe qui n'a pas encore eu lieu
 * (event_date dans le futur — vérifié côté service). Ne touche pas à la
 * liste des membres concernés ni aux montants déjà en cours : seuls les
 * champs descriptifs et la planification sont modifiables ici.
 */
public class GroupEventUpdateRequest {

    @NotBlank(message = "Le titre de l'événement est obligatoire.")
    private String title;

    private String description;

    // Optionnel — voir GroupEventRequest.
    @DecimalMin(value = "0.0", inclusive = true, message = "Le montant cible ne peut pas être négatif.")
    private BigDecimal targetAmount;

    @NotNull(message = "La date de l'événement est obligatoire.")
    private LocalDate eventDate;

    // Optionnelle, comme sur GroupEventRequest — voir cette classe.
    private java.time.LocalTime eventTime;

    @NotNull(message = "Le type de récurrence est obligatoire.")
    private RepetitionType repetitionType;

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

    public RepetitionType getRepetitionType() {
        return repetitionType;
    }

    public void setRepetitionType(RepetitionType repetitionType) {
        this.repetitionType = repetitionType;
    }
}
