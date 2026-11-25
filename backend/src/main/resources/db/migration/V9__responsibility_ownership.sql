-- V1 placeholders cannot be upgraded without inventing an owner and a title.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM responsibilities) THEN
    RAISE EXCEPTION 'Existing responsibilities require an approved data-preserving migration before V9';
  END IF;
END $$;

ALTER TABLE household_memberships
  ADD CONSTRAINT uq_membership_id_household UNIQUE (id, household_id);

ALTER TABLE responsibilities
  ADD COLUMN title TEXT NOT NULL,
  ADD COLUMN description TEXT,
  ADD COLUMN mode TEXT NOT NULL,
  ADD COLUMN status TEXT NOT NULL,
  ADD COLUMN owner_membership_id UUID NOT NULL,
  ADD COLUMN version BIGINT NOT NULL,
  -- Match Java 25 Character.isWhitespace (the predicate used by String.isBlank).
  -- Explicit code points avoid PostgreSQL locale-dependent POSIX space classifications.
  ADD CONSTRAINT ck_responsibility_title CHECK (length(translate(title,
    chr(9)||chr(10)||chr(11)||chr(12)||chr(13)||chr(28)||chr(29)||chr(30)||chr(31)||
    chr(32)||chr(5760)||chr(8192)||chr(8193)||chr(8194)||chr(8195)||chr(8196)||
    chr(8197)||chr(8198)||chr(8200)||chr(8201)||chr(8202)||chr(8232)||chr(8233)||
    chr(8287)||chr(12288), '')) > 0),
  ADD CONSTRAINT ck_responsibility_mode CHECK (mode = 'INDIVIDUAL'),
  ADD CONSTRAINT ck_responsibility_status CHECK (status IN ('ACTIVE', 'PAUSED', 'ARCHIVED')),
  ADD CONSTRAINT ck_responsibility_version CHECK (version >= 0),
  ADD CONSTRAINT fk_responsibility_owner_household
    FOREIGN KEY (owner_membership_id, household_id)
    REFERENCES household_memberships (id, household_id);

CREATE TABLE responsibility_owner_transfers (
  id UUID PRIMARY KEY,
  responsibility_id UUID NOT NULL,
  household_id UUID NOT NULL,
  actor_account_id UUID NOT NULL REFERENCES accounts(id),
  occurred_at TIMESTAMPTZ NOT NULL,
  previous_owner_membership_id UUID NOT NULL,
  new_owner_membership_id UUID NOT NULL,
  CONSTRAINT ck_transfer_distinct_owners CHECK (previous_owner_membership_id <> new_owner_membership_id),
  CONSTRAINT fk_transfer_responsibility FOREIGN KEY (responsibility_id, household_id)
    REFERENCES responsibilities (id, household_id),
  CONSTRAINT fk_transfer_old_owner FOREIGN KEY (previous_owner_membership_id, household_id)
    REFERENCES household_memberships (id, household_id),
  CONSTRAINT fk_transfer_new_owner FOREIGN KEY (new_owner_membership_id, household_id)
    REFERENCES household_memberships (id, household_id)
);

CREATE FUNCTION reject_transfer_fact_update() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
  RAISE EXCEPTION 'Responsibility ownership transfer facts are immutable' USING ERRCODE = '23514';
END $$;
CREATE TRIGGER responsibility_transfer_immutable BEFORE UPDATE ON responsibility_owner_transfers
  FOR EACH ROW EXECUTE FUNCTION reject_transfer_fact_update();

-- All ownership changes and member deactivations serialize on the household row.
CREATE FUNCTION check_responsibility_active_owner() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
  PERFORM 1 FROM households WHERE id = NEW.household_id FOR UPDATE;
  IF NEW.status IN ('ACTIVE', 'PAUSED') AND NOT EXISTS (
    SELECT 1 FROM household_memberships
    WHERE id = NEW.owner_membership_id AND household_id = NEW.household_id AND active
  ) THEN
    RAISE EXCEPTION 'Current responsibility requires an active owner in the same household'
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER responsibility_active_owner BEFORE INSERT OR UPDATE ON responsibilities
  FOR EACH ROW EXECUTE FUNCTION check_responsibility_active_owner();

CREATE FUNCTION prevent_owned_membership_deactivation() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
  IF OLD.active AND (TG_OP = 'DELETE' OR NOT NEW.active OR NEW.household_id <> OLD.household_id OR NEW.id <> OLD.id OR NEW.account_id <> OLD.account_id) THEN
    PERFORM 1 FROM households WHERE id = OLD.household_id FOR UPDATE;
    IF EXISTS (SELECT 1 FROM responsibilities WHERE household_id = OLD.household_id
      AND owner_membership_id = OLD.id AND status IN ('ACTIVE', 'PAUSED')) THEN
      RAISE EXCEPTION 'Active responsibility owner cannot be deactivated'
        USING ERRCODE = '23514';
    END IF;
  END IF;
  IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER membership_responsibility_owner BEFORE UPDATE OR DELETE ON household_memberships
  FOR EACH ROW EXECUTE FUNCTION prevent_owned_membership_deactivation();
