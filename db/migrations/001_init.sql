-- DC Chat India: initial schema. Safe to run more than once (IF NOT EXISTS).
-- Everything lives in its own schema "dcchat" so it can never collide with
-- other tables already in your Supabase project, and Supabase's public REST API
-- (which only exposes the "public" schema) cannot reach it.

CREATE SCHEMA IF NOT EXISTS dcchat;

CREATE TABLE IF NOT EXISTS dcchat.users (
  uid            text PRIMARY KEY,
  email          text NOT NULL,
  email_key      text NOT NULL UNIQUE,
  name           text NOT NULL,
  password_hash  text NOT NULL,
  role           text NOT NULL DEFAULT 'user' CHECK (role IN ('user','admin')),
  blocked        boolean NOT NULL DEFAULT false,
  token_version  integer NOT NULL DEFAULT 0,
  created_at     timestamptz NOT NULL DEFAULT now(),
  last_seen      timestamptz
);

CREATE TABLE IF NOT EXISTS dcchat.contacts (
  owner_uid    text NOT NULL REFERENCES dcchat.users(uid) ON DELETE CASCADE,
  contact_uid  text NOT NULL REFERENCES dcchat.users(uid) ON DELETE CASCADE,
  created_at   timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (owner_uid, contact_uid),
  CHECK (owner_uid <> contact_uid)
);

CREATE TABLE IF NOT EXISTS dcchat.messages (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  from_uid      text NOT NULL REFERENCES dcchat.users(uid) ON DELETE CASCADE,
  to_uid        text NOT NULL REFERENCES dcchat.users(uid) ON DELETE CASCADE,
  body          text NOT NULL CHECK (char_length(body) BETWEEN 1 AND 4000),
  created_at    timestamptz NOT NULL DEFAULT now(),
  delivered_at  timestamptz,
  read_at       timestamptz
);
CREATE INDEX IF NOT EXISTS messages_pair_idx   ON dcchat.messages (from_uid, to_uid, created_at DESC);
CREATE INDEX IF NOT EXISTS messages_to_idx     ON dcchat.messages (to_uid, created_at DESC);
CREATE INDEX IF NOT EXISTS messages_unread_idx ON dcchat.messages (to_uid, from_uid) WHERE read_at IS NULL;

CREATE TABLE IF NOT EXISTS dcchat.password_resets (
  email_key   text PRIMARY KEY,
  code_hash   text NOT NULL,
  expires_at  timestamptz NOT NULL,
  attempts    integer NOT NULL DEFAULT 0,
  created_at  timestamptz NOT NULL DEFAULT now()
);

-- Defence in depth: the server connects with its private database connection.
-- No policies are granted to anon/authenticated users; RLS blocks client-side access.
ALTER TABLE dcchat.users           ENABLE ROW LEVEL SECURITY;
ALTER TABLE dcchat.contacts        ENABLE ROW LEVEL SECURITY;
ALTER TABLE dcchat.messages        ENABLE ROW LEVEL SECURITY;
ALTER TABLE dcchat.password_resets ENABLE ROW LEVEL SECURITY;
