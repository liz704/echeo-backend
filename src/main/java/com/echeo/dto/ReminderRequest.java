package com.echeo.dto;

import com.echeo.model.enums.RepetitionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public class ReminderRequest {

    @NotBlank(message = "Le titre est obligatoire.")
    private String title;

    private String description;

    @NotNull(message = "La date d'échéance est obligatoire.")
    private LocalDate dueDate;

    private LocalTime dueTime;

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
}
