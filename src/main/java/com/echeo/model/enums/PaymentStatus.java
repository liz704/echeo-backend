package com.echeo.model.enums;

/**
 * Statut d'un membre pour un événement de groupe.
 *
 * Deux familles de valeurs cohabitent selon que l'événement porte de
 * l'argent ou non (GroupEvent.targetAmount null = sans argent) :
 * - Avec argent   : PENDING, PARTIALLY_PAID, PAID, SURPLUS, OVERDUE.
 * - Sans argent   : NOT_SEEN, SEEN, OVERDUE (accusé de lecture).
 * OVERDUE est partagé entre les deux cas : dette impayée à l'échéance,
 * ou rappel jamais vu à l'échéance — même sémantique de "en retard".
 */
public enum PaymentStatus {
    PENDING,
    PARTIALLY_PAID,
    PAID,
    SURPLUS,
    OVERDUE,
    NOT_SEEN,
    SEEN
}
