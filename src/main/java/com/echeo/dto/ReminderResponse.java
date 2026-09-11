package com.echeo.dto;

import com.echeo.model.entity.PersonalReminder;
import com.echeo.model.enums.RepetitionType;

import java.time.LocalDate;
import java.time.LocalTime;

public class ReminderResponse {

    private Long id;
    private String title;
    private String description;
    private LocalDate dueDate;
    private LocalTime dueTime;
    private RepetitionType repetitionType;
    private boolean completed;
    private LocalDate nextOccurrence;

    public static ReminderResponse from(PersonalReminder reminder) {
        ReminderResponse response = new ReminderResponse();
        response.id = reminder.getId();
        response.title = reminder.getTitle();
        response.description = reminder.getDescription();
        response.dueDate = reminder.getDueDate();
        response.dueTime = reminder.getDueTime();
        response.repetitionType = reminder.getRepetitionType();
        response.completed = reminder.isCompleted();
        response.nextOccurrence = reminder.getNextOccurrence();
        return response;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalTime getDueTime() {
        return dueTime;
    }

    public RepetitionType getRepetitionType() {
        return repetitionType;
    }

    public boolean isCompleted() {
        return completed;
    }

    public LocalDate getNextOccurrence() {
        return nextOccurrence;
    }
}
