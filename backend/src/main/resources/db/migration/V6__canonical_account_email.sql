ALTER TABLE accounts ADD CONSTRAINT ck_accounts_email_canonical
  CHECK (email = lower(email) AND email !~ '^[[:space:]]|[[:space:]]$');
