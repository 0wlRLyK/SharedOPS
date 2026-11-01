CREATE TABLE households (
  id UUID PRIMARY KEY
);

CREATE TABLE responsibilities (
  id UUID PRIMARY KEY,
  household_id UUID NOT NULL ,
  CONSTRAINT fk_households
  FOREIGN KEY (household_id)
  REFERENCES households(id),
  UNIQUE (id, household_id)
);
CREATE TABLE tasks (
  id UUID PRIMARY KEY,
  household_id UUID NOT NULL,
  responsibility_id UUID,
  CONSTRAINT fk_tasks_responsibility_household
  FOREIGN KEY (responsibility_id, household_id)
  REFERENCES responsibilities(id, household_id),
  CONSTRAINT fk_tasks_household
  FOREIGN KEY (household_id)
  REFERENCES households(id)
);

create TABLE checklist_entries (
  id UUID PRIMARY KEY,
  task_id UUID NOT NULL,
  text VARCHAR NOT NULL,
  is_completed BOOLEAN NOT NULL default false,
  position int default 0 NOT NULL,
  CONSTRAINT fk_checklist_entries_task
  FOREIGN KEY (task_id)
  REFERENCES tasks(id)
  ON DELETE CASCADE,
  CONSTRAINT non_negative_position check (position >= 0),
  UNIQUE (task_id, position)

);
