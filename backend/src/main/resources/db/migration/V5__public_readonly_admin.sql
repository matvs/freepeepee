-- V5__public_readonly_admin.sql
-- Public read-only model: the seeded dev user is removed and the real admin is provisioned
-- from environment variables at startup. The toilet/audit data must survive that removal.

-- toilet: keep referential integrity but let a user be removed without losing toilet rows.
ALTER TABLE toilet DROP CONSTRAINT IF EXISTS toilet_created_by_fkey;
ALTER TABLE toilet ADD CONSTRAINT toilet_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE SET NULL;

-- audit_entry is append-only: trigger trg_audit_no_update blocks every UPDATE/DELETE.
-- A FK with ON DELETE SET NULL would make deleting a referenced user cascade an UPDATE
-- onto audit_entry, which the trigger rejects ("audit_entry is append-only"). Drop the FK
-- entirely instead — the immutable log keeps the actor's identity in actor_name, so a soft
-- (un-enforced) actor_id is the correct trade-off for an append-only table.
ALTER TABLE audit_entry DROP CONSTRAINT IF EXISTS audit_entry_actor_id_fkey;

-- Remove the dev-seeded credentials (matvs / lap00p00). Toilets' created_by becomes NULL;
-- audit rows keep their actor_id value and actor_name (no cascade touches audit_entry now).
DELETE FROM app_user WHERE username = 'matvs';
