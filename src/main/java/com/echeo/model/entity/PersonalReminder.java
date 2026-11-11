package com.echeo.model.entity;

import com.echeo.model.enums.RepetitionType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Personal reminder created by a user, with recurrence handling
 * (repetition_type) and computation of the next occurrence (next_occurrence).
 */
@Entity
@Table(name = "personal_reminders")
public class PersonalReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Lazy loading: we do not want to load the whole user on every reminder read.
    // Its collections are ignored during serialization to avoid JSON loops and
    // to expose only the useful fields.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"passwordHash"})
    private User user;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "due_time")
    private LocalTime dueTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "repetition_type", nullable = false, length = 20)
    private RepetitionType repetitionType = RepetitionType.NONE;

    @Column(name = "is_completed", nullable = false)
    private boolean completed = false;

    @Column(name = "next_occurrence")
    private LocalDate nextOccurrence;

    // A paused reminder stays in the active list but sends no e-mail and is never
    // rolled forward automatically (see ReminderService.pauseReminder / resumeReminder).
    @Column(name = "is_paused", nullable = false)
    private boolean paused = false;

    // Timestamp of the last e-mail actually sent for this reminder (null until one
    // is sent). Reset to null whenever the date/time changes so the reminder is
    // re-armed. See PersonalReminderNotificationService.
    @Column(name = "notification_sent_at")
    private java.time.OffsetDateTime notificationSentAt;

    // Public token allowing the reminder to be marked as done straight from a link
    // in the e-mail, without authentication. Generated once at creation
    // (see ReminderService.createReminder).
    @Column(name = "public_completion_token", unique = true)
    private java.util.UUID publicCompletionToken;

    public PersonalReminder() {
    }

    public PersonalReminder(Long id, User user, String title, String description, LocalDate dueDate,
                             LocalTime dueTime, RepetitionType repetitionType, boolean completed,
                             LocalDate nextOccurrence) {
        this.id = id;
        this.user = user;
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        this.dueTime = dueTime;
        this.repetitionType = repetitionType;
        this.completed = completed;
        this.nextOccurrence = nextOccurrence;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalTime getDueTime() {
        return dueTime;
    }

    public void setDueTime(LocalTime dueTime) {
        this.dueTime = dueTime;
    }

    public RepetitionType getRepetitionType() {
        return repetitionType;
    }

    public void setRepetitionType(RepetitionType repetitionType) {
        this.repetitionType = repetitionType;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
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

    public java.time.OffsetDateTime getNotificationSentAt() {
        return notificationSentAt;
    }

    public void setNotificationSentAt(java.time.OffsetDateTime notificationSentAt) {
        this.notificationSentAt = notificationSentAt;
    }

    public java.util.UUID getPublicCompletionToken() {
        return publicCompletionToken;
    }

    public void setPublicCompletionToken(java.util.UUID publicCompletionToken) {
        this.publicCompletionToken = publicCompletionToken;
    }
}
