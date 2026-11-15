CREATE TABLE accounts (
  id UUID PRIMARY KEY,
  email VARCHAR(254) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL default now(),
  CONSTRAINT uq_accounts_email UNIQUE(email)
);
