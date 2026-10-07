package com.echeo.dto;

import com.echeo.model.entity.User;

import java.time.OffsetDateTime;

public class UserResponse {

    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String preferredLocale;
    private boolean weekPlanEnabled;
    private int weekPlanDay;
    private String weekPlanSendTime;
    private String role;
    private OffsetDateTime createdAt;

    public static UserResponse from(User user) {
        UserResponse response = new UserResponse();
        response.id = user.getId();
        response.fullName = user.getFullName();
        response.email = user.getEmail();
        response.phone = user.getPhone();
        response.role = user.getRole().name();
        response.createdAt = user.getCreatedAt();
        response.preferredLocale = user.getPreferredLocale();
        response.weekPlanEnabled = user.isWeekPlanEnabled();
        response.weekPlanDay = user.getWeekPlanDay();
        if (user.getWeekPlanSendTime() != null) {
            response.weekPlanSendTime = user.getWeekPlanSendTime().toString();
        }
        return response;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getRole() {
        return role;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getPreferredLocale() { return preferredLocale; }
    public void setPreferredLocale(String preferredLocale) { this.preferredLocale = preferredLocale; }

    public boolean isWeekPlanEnabled() { return weekPlanEnabled; }
    public void setWeekPlanEnabled(boolean weekPlanEnabled) { this.weekPlanEnabled = weekPlanEnabled; }
    public int getWeekPlanDay() { return weekPlanDay; }
    public void setWeekPlanDay(int weekPlanDay) { this.weekPlanDay = weekPlanDay; }
    public String getWeekPlanSendTime() { return weekPlanSendTime; }
    public void setWeekPlanSendTime(String weekPlanSendTime) { this.weekPlanSendTime = weekPlanSendTime; }
}
