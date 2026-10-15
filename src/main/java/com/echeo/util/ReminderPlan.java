package com.echeo.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Plan de relances (classe pure, testable seule).
 *
 * AVANT l'échéance : jours choisis par l'organisateur (liste facultative, ex. "3,1,0").
 *   Aucune relance obligatoire, pas même le jour J. Si rien n'est choisi : 7,3,0 (historique).
 *   Valeur "none" : aucune relance avant l'échéance.
 * APRÈS l'échéance (retard) :
 *   - événement avec argent : calendrier fixe jusqu'au paiement :
 *     J+1, J+2, J+3, J+5, J+7, puis une fois par semaine (J+14, J+21, ...), 8 semaines max.
 *   - événement sans argent : une seule relance, le lendemain (J+1), si non vu.
 * Jamais plus d'une relance par membre et par jour (géré par l'appelant).
 */
public final class ReminderPlan {

    public static final String DEFAULT_BEFORE = "7,3,0";
    public static final String NONE = "none";
    private static final int[] OVERDUE_MONEY_FIXED = {1, 2, 3, 5, 7};
    private static final int MAX_OVERDUE_DAYS = 56;

    private ReminderPlan() {
    }

    /** Nettoie la saisie ("3, 1,0,x" -> "3,1,0"). Retourne null si vide (= défaut). */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.trim().equalsIgnoreCase(NONE)) {
            return NONE;
        }
        TreeSet<Integer> days = new TreeSet<>(Comparator.reverseOrder());
        for (String part : raw.split(",")) {
            try {
                int d = Integer.parseInt(part.trim());
                if (d >= 0 && d <= 60) {
                    days.add(d);
                }
            } catch (NumberFormatException ignored) {
                // valeur invalide ignorée
            }
        }
        if (days.isEmpty()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (Integer d : days) {
            out.add(String.valueOf(d));
        }
        return String.join(",", out);
    }

    public static List<Integer> beforeDays(String stored) {
        String v = (stored == null || stored.isBlank()) ? DEFAULT_BEFORE : stored;
        List<Integer> out = new ArrayList<>();
        if (v.equals(NONE)) {
            return out;
        }
        for (String part : v.split(",")) {
            try {
                out.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // ignoré
            }
        }
        return out;
    }

    /** Faut-il relancer aujourd'hui AVANT l'échéance ? daysUntil = jours restants (0 = jour J). */
    public static boolean shouldRemindBefore(String stored, long daysUntil) {
        return daysUntil >= 0 && beforeDays(stored).contains((int) daysUntil);
    }

    /** Faut-il relancer aujourd'hui APRÈS l'échéance ? daysOverdue >= 1. */
    public static boolean shouldRemindOverdue(boolean hasMoney, long daysOverdue) {
        if (daysOverdue < 1) {
            return false;
        }
        if (!hasMoney) {
            return daysOverdue == 1;
        }
        if (daysOverdue > MAX_OVERDUE_DAYS) {
            return false;
        }
        for (int d : OVERDUE_MONEY_FIXED) {
            if (d == daysOverdue) {
                return true;
            }
        }
        return daysOverdue > 7 && daysOverdue % 7 == 0;
    }
}
