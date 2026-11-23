DO $$
BEGIN
  IF EXISTS (
    SELECT household_id FROM household_memberships WHERE active
    GROUP BY household_id HAVING COUNT(*) > 2
  ) THEN
    RAISE EXCEPTION 'Cannot allocate membership slots: household exceeds two active members';
  END IF;
END $$;

ALTER TABLE household_memberships ADD COLUMN slot SMALLINT;
WITH numbered AS (
  SELECT id, ROW_NUMBER() OVER (PARTITION BY household_id ORDER BY id) AS slot
  FROM household_memberships WHERE active
)
UPDATE household_memberships m SET slot = numbered.slot
FROM numbered WHERE m.id = numbered.id;
ALTER TABLE household_memberships ADD CONSTRAINT ck_household_membership_slot
  CHECK ((active AND slot IS NOT NULL AND slot IN (1, 2)) OR (NOT active AND slot IS NULL));
CREATE UNIQUE INDEX uq_household_memberships_active_slot
  ON household_memberships(household_id, slot) WHERE active;

CREATE TABLE household_invitations (
  id UUID PRIMARY KEY,
  household_id UUID NOT NULL REFERENCES households(id),
  token_hash BYTEA NOT NULL UNIQUE CHECK (octet_length(token_hash) = 32),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  consumed_at TIMESTAMP WITH TIME ZONE,
  revoked_at TIMESTAMP WITH TIME ZONE,
  CONSTRAINT ck_invitation_times CHECK (
    expires_at > created_at
    AND (consumed_at IS NULL OR consumed_at >= created_at)
    AND (revoked_at IS NULL OR revoked_at >= created_at)
  ),
  CONSTRAINT ck_invitation_terminal CHECK (consumed_at IS NULL OR revoked_at IS NULL)
);
