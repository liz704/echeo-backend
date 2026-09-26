package com.echeo.dto;

import com.echeo.model.entity.GroupEvent;

import java.util.List;

/**
 * Historique d'un groupe : événements passés + versements enregistrés.
 */
public class GroupHistoryResponse {

    private List<GroupEvent> pastEvents;
    private List<GroupPaymentHistoryItem> payments;

    public GroupHistoryResponse() {
    }

    public GroupHistoryResponse(List<GroupEvent> pastEvents, List<GroupPaymentHistoryItem> payments) {
        this.pastEvents = pastEvents;
        this.payments = payments;
    }

    public List<GroupEvent> getPastEvents() {
        return pastEvents;
    }

    public void setPastEvents(List<GroupEvent> pastEvents) {
        this.pastEvents = pastEvents;
    }

    public List<GroupPaymentHistoryItem> getPayments() {
        return payments;
    }

    public void setPayments(List<GroupPaymentHistoryItem> payments) {
        this.payments = payments;
    }
}
