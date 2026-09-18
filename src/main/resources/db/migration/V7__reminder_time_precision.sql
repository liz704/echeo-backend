-- ============================================================================
-- ÉCHÉO - Migration V7 : respect de l'heure précise réglée par l'utilisateur
--
-- Jusqu'ici, les relances (perso et groupe) ignoraient l'heure choisie et
-- partaient toutes à une heure fixe une fois par jour. Cette migration :
--
-- 1. personal_reminders.notification_sent_at : verrou anti-doublon pour le
--    rappel personnel (email envoyé une seule fois, à l'heure due_time).
--
-- 2. group_events.event_time : heure réglée par le créateur du groupe pour
--    l'événement (optionnelle — NULL = comportement par défaut, 08:00).
--
-- 3. event_member_status.last_reminder_stage / last_reminder_sent_at :
--    verrou anti-doublon par membre et par étape (J-7, J0, J+3 en retard...)
--    pour la relance de groupe, désormais scrutée toutes les minutes au lieu
--    d'une fois par jour, afin de respecter event_time.
-- ============================================================================

ALTER TABLE personal_reminders
    ADD COLUMN notification_sent_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE group_events
    ADD COLUMN event_time TIME;

ALTER TABLE event_member_status
    ADD COLUMN last_reminder_stage VARCHAR(20);

ALTER TABLE event_member_status
    ADD COLUMN last_reminder_sent_at TIMESTAMP WITH TIME ZONE;
