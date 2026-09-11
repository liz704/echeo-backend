package com.echeo.model.entity;

import com.echeo.model.enums.RepetitionType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Événement de groupe donnant lieu à une cotisation (montant cible)
 * à répartir/suivre par membre via EventMemberStatus.
 *
 * Peut être récurrent (repetitionType, comme un rappel personnel) : à
 * l'échéance, une nouvelle occurrence est générée automatiquement, avec
 * report intelligent du solde (voir GroupService.generateNextOccurrence).
 * isPaused suspend cette reconduction sans supprimer l'événement.
 */
@Entity
@Table(name = "group_events")
public class GroupEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    @JsonIgnoreProperties({"members", "events"})
    private Group group;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "target_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "repetition_type", nullable = false, length = 20)
    private RepetitionType repetitionType = RepetitionType.NONE;

    @Column(name = "next_occurrence")
    private LocalDate nextOccurrence;

    @Column(name = "is_paused", nullable = false)
    private boolean paused = false;

    // Suppression réelle en cascade : effacer un événement doit
    // effacer tous les statuts de paiement associés.
    @JsonIgnore
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<EventMemberStatus> memberStatuses = new ArrayList<>();

    public GroupEvent() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
    }

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

    public RepetitionType getRepetitionType() {
        return repetitionType;
    }

    public void setRepetitionType(RepetitionType repetitionType) {
        this.repetitionType = repetitionType;
    }

    public LocalDate getNextOccurrence() {
        return nextOccurrence;
    }

    public void setNextOccurrence(LocalDate nextOccurrence) {
        this.nextOccurrence = nextOccurrence;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public List<EventMemberStatus> getMemberStatuses() {
        return memberStatuses;
    }

    public void setMemberStatuses(List<EventMemberStatus> memberStatuses) {
        this.memberStatuses = memberStatuses;
    }
}
