-- V5__public_readonly_admin.sql
-- Public read-only model: the seeded dev user is removed and the real admin is provisioned
-- from environment variables at startup. Relax FKs so deleting a user never destroys
-- toilet rows or audit history (audit_entry.actor_name is denormalized text and survives).

ALTER TABLE toilet DROP CONSTRAINT IF EXISTS toilet_created_by_fkey;
ALTER TABLE toilet ADD CONSTRAINT toilet_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE SET NULL;

ALTER TABLE audit_entry DROP CONSTRAINT IF EXISTS audit_entry_actor_id_fkey;
ALTER TABLE audit_entry ADD CONSTRAINT audit_entry_actor_id_fkey
    FOREIGN KEY (actor_id) REFERENCES app_user(id) ON DELETE SET NULL;

-- Remove the dev-seeded credentials (matvs / lap00p00). Toilets' created_by becomes NULL.
DELETE FROM app_user WHERE username = 'matvs';
