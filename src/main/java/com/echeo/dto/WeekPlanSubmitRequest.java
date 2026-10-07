package com.echeo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalTime;
import java.util.List;

public class WeekPlanSubmitRequest {

    @NotEmpty
    @Valid
    private List<WeekPlanTaskRequest> tasks;

    /** Heure utilisée si une tâche n'a pas d'heure. */
    private LocalTime defaultTime;

    public List<WeekPlanTaskRequest> getTasks() { return tasks; }
    public void setTasks(List<WeekPlanTaskRequest> tasks) { this.tasks = tasks; }
    public LocalTime getDefaultTime() { return defaultTime; }
    public void setDefaultTime(LocalTime defaultTime) { this.defaultTime = defaultTime; }
}
