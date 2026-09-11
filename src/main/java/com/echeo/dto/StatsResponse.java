package com.echeo.dto;

import java.math.BigDecimal;

/**
 * DTO global de statistiques, agrégeant l'état des rappels personnels
 * et des paiements de groupe sur toute la plateforme.
 */
public class StatsResponse {

    private long totalReminders;
    private long completedReminders;
    private long pendingReminders;

    private BigDecimal totalExpectedAmount;
    private BigDecimal totalCollectedAmount;
    private BigDecimal totalRemainingAmount;
    private BigDecimal totalSurplusAmount;

    public StatsResponse() {
    }

    public StatsResponse(long totalReminders, long completedReminders, long pendingReminders,
                          BigDecimal totalExpectedAmount, BigDecimal totalCollectedAmount,
                          BigDecimal totalRemainingAmount, BigDecimal totalSurplusAmount) {
        this.totalReminders = totalReminders;
        this.completedReminders = completedReminders;
        this.pendingReminders = pendingReminders;
        this.totalExpectedAmount = totalExpectedAmount;
        this.totalCollectedAmount = totalCollectedAmount;
        this.totalRemainingAmount = totalRemainingAmount;
        this.totalSurplusAmount = totalSurplusAmount;
    }

    public long getTotalReminders() {
        return totalReminders;
    }

    public long getCompletedReminders() {
        return completedReminders;
    }

    public long getPendingReminders() {
        return pendingReminders;
    }

    public BigDecimal getTotalExpectedAmount() {
        return totalExpectedAmount;
    }

    public BigDecimal getTotalCollectedAmount() {
        return totalCollectedAmount;
    }

    public BigDecimal getTotalRemainingAmount() {
        return totalRemainingAmount;
    }

    public BigDecimal getTotalSurplusAmount() {
        return totalSurplusAmount;
    }
}
