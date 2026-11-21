DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM households) THEN
    RAISE EXCEPTION 'Existing households require an approved migration before V7';
  END IF;
END $$;

ALTER TABLE households
  ADD COLUMN name VARCHAR(100) NOT NULL,
  ADD COLUMN icon VARCHAR(100),
  ADD COLUMN currency VARCHAR(3) NOT NULL,
  ADD COLUMN timezone VARCHAR(100) NOT NULL;

CREATE TABLE household_memberships (
  id UUID PRIMARY KEY,
  account_id UUID NOT NULL REFERENCES accounts(id),
  household_id UUID NOT NULL REFERENCES households(id),
  active BOOLEAN NOT NULL
);
CREATE UNIQUE INDEX uq_household_memberships_active_account
  ON household_memberships(account_id) WHERE active;
