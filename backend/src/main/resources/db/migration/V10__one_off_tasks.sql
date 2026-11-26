-- Placeholder tasks lack the fields required by the new model; never invent user data.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM tasks) THEN
    RAISE EXCEPTION 'Existing tasks require an approved data-preserving migration before V10';
  END IF;
END $$;

ALTER TABLE tasks
  ADD COLUMN title TEXT NOT NULL,
  ADD COLUMN description TEXT,
  ADD COLUMN priority TEXT NOT NULL,
  ADD COLUMN status TEXT NOT NULL,
  ADD COLUMN version BIGINT NOT NULL,
  ADD COLUMN assignee_membership_id UUID,
  ADD COLUMN deadline_kind TEXT NOT NULL,
  ADD COLUMN due_date_iso TEXT,
  ADD COLUMN due_zone TEXT,
  ADD COLUMN due_epoch_second BIGINT,
  ADD COLUMN due_nano INTEGER,
  ADD CONSTRAINT uq_tasks_id_household UNIQUE (id, household_id),
  ADD CONSTRAINT fk_tasks_assignee_household FOREIGN KEY (assignee_membership_id, household_id)
    REFERENCES household_memberships (id, household_id),
  ADD CONSTRAINT ck_task_title CHECK (length(translate(title,
    chr(9)||chr(10)||chr(11)||chr(12)||chr(13)||chr(28)||chr(29)||chr(30)||chr(31)||
    chr(32)||chr(5760)||chr(8192)||chr(8193)||chr(8194)||chr(8195)||chr(8196)||
    chr(8197)||chr(8198)||chr(8200)||chr(8201)||chr(8202)||chr(8232)||chr(8233)||
    chr(8287)||chr(12288), '')) > 0),
  ADD CONSTRAINT ck_task_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH')),
  ADD CONSTRAINT ck_task_status CHECK (status IN ('OPEN', 'COMPLETED', 'CANCELED', 'SKIPPED')),
  ADD CONSTRAINT ck_task_version CHECK (version >= 0),
  ADD CONSTRAINT ck_task_deadline CHECK (
    (deadline_kind = 'NONE' AND due_date_iso IS NULL AND due_zone IS NULL AND due_epoch_second IS NULL AND due_nano IS NULL)
    OR (deadline_kind = 'DATE' AND due_date_iso IS NOT NULL AND due_zone IS NOT NULL AND due_epoch_second IS NULL AND due_nano IS NULL)
    OR (deadline_kind = 'DATE_TIME' AND due_date_iso IS NULL AND due_zone IS NULL AND due_epoch_second IS NOT NULL AND due_nano IS NOT NULL AND due_nano BETWEEN 0 AND 999999999)
  );

CREATE TABLE task_edit_facts (
  id UUID PRIMARY KEY,
  task_id UUID NOT NULL,
  household_id UUID NOT NULL,
  actor_account_id UUID NOT NULL REFERENCES accounts(id),
  occurred_at TIMESTAMPTZ NOT NULL,
  previous_version BIGINT NOT NULL,
  new_version BIGINT NOT NULL,
  changed_fields TEXT[] NOT NULL,
  CONSTRAINT fk_edit_fact_task FOREIGN KEY (task_id, household_id) REFERENCES tasks(id, household_id),
  CONSTRAINT ck_edit_fact_version CHECK (previous_version >= 0 AND new_version > 0 AND new_version - 1 = previous_version),
  CONSTRAINT ck_edit_fact_fields CHECK (cardinality(changed_fields) > 0 AND array_position(changed_fields, NULL) IS NULL AND changed_fields <@ ARRAY['TITLE','DESCRIPTION','DEADLINE','PRIORITY','ASSIGNMENT']::TEXT[])
);

CREATE FUNCTION reject_task_edit_fact_update() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
  RAISE EXCEPTION 'Task edit facts are immutable' USING ERRCODE = '23514';
END $$;
CREATE TRIGGER task_edit_fact_immutable BEFORE UPDATE ON task_edit_facts
  FOR EACH ROW EXECUTE FUNCTION reject_task_edit_fact_update();
