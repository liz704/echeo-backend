package com.echeo.dto;

import com.echeo.model.enums.RepetitionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Vue détaillée d'un événement de groupe : infos de l'événement +
 * suivi indépendant de chaque membre concerné (statuts + historique).
 */
public class EventDetailResponse {

    private Long eventId;
    private Long groupId;
    private String title;
    private String description;
    private BigDecimal targetAmount;
    private BigDecimal withdrawalFeeAmount;
    private LocalDate eventDate;
    private LocalTime eventTime;
    private RepetitionType repetitionType;
    private boolean paused;
    private boolean hasMoney;
    private List<EventMemberDetailItem> members = new ArrayList<>();

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public void setTargetAmount(BigDecimal targetAmount) { this.targetAmount = targetAmount; }
    public BigDecimal getWithdrawalFeeAmount() { return withdrawalFeeAmount; }
    public void setWithdrawalFeeAmount(BigDecimal withdrawalFeeAmount) { this.withdrawalFeeAmount = withdrawalFeeAmount; }
    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }
    public LocalTime getEventTime() { return eventTime; }
    public void setEventTime(LocalTime eventTime) { this.eventTime = eventTime; }
    public RepetitionType getRepetitionType() { return repetitionType; }
    public void setRepetitionType(RepetitionType repetitionType) { this.repetitionType = repetitionType; }
    public boolean isPaused() { return paused; }
    public void setPaused(boolean paused) { this.paused = paused; }
    public boolean isHasMoney() { return hasMoney; }
    public void setHasMoney(boolean hasMoney) { this.hasMoney = hasMoney; }
    public List<EventMemberDetailItem> getMembers() { return members; }
    public void setMembers(List<EventMemberDetailItem> members) { this.members = members; }
}
