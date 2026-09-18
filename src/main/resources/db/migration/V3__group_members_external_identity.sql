-- ============================================================================
-- ÉCHÉO - Migration V3 : membres de groupe externes (sans compte ÉCHÉO)
--
-- Un membre de groupe n'a plus besoin d'être un utilisateur inscrit :
-- nom + email suffisent (le téléphone reste optionnel). Un membre AVEC
-- compte reste possible (user_id renseigné) pour ceux qui utilisent
-- l'application ; les deux cas cohabitent dans la même table.
--
-- event_member_status référence désormais group_members (et non plus
-- directement users), puisque le "membre concerné" par une échéance
-- peut être quelqu'un sans compte.
-- ============================================================================

-- --- 1. group_members : user_id devient optionnel, ajout de l'identité externe ---

ALTER TABLE group_members
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE group_members
    ADD COLUMN external_full_name VARCHAR(255),
    ADD COLUMN external_email VARCHAR(255),
    ADD COLUMN external_phone VARCHAR(30);

-- Un membre doit avoir SOIT un compte (user_id), SOIT une identité externe
-- complète (nom + email). Jamais aucun des deux, jamais un mélange partiel.
ALTER TABLE group_members
    ADD CONSTRAINT chk_group_member_identity CHECK (
        (user_id IS NOT NULL AND external_full_name IS NULL AND external_email IS NULL)
        OR
        (user_id IS NULL AND external_full_name IS NOT NULL AND external_email IS NOT NULL)
    );

CREATE INDEX idx_group_members_external_email ON group_members (external_email);

-- --- 2. event_member_status : référence group_members au lieu de users ---

ALTER TABLE event_member_status
    DROP CONSTRAINT uq_event_member;

ALTER TABLE event_member_status
    ADD COLUMN group_member_id BIGINT REFERENCES group_members (id) ON DELETE CASCADE;

-- Migration des données existantes : retrouve le group_member correspondant
-- via le group_id de l'événement et le member_id (user) actuel.
UPDATE event_member_status ems
SET group_member_id = gm.id
FROM group_events ge, group_members gm
WHERE ems.event_id = ge.id
  AND gm.group_id = ge.group_id
  AND gm.user_id = ems.member_id;

ALTER TABLE event_member_status
    ALTER COLUMN group_member_id SET NOT NULL;

ALTER TABLE event_member_status
    DROP COLUMN member_id;

ALTER TABLE event_member_status
    ADD CONSTRAINT uq_event_member UNIQUE (event_id, group_member_id);

DROP INDEX IF EXISTS idx_ems_member_id;
CREATE INDEX idx_ems_group_member_id ON event_member_status (group_member_id);
