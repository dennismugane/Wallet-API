-- V1__initial_schema.sql
-- Flyway manages all schema changes. Never edit this file after it has been
-- applied to any environment. Create a new V2__ file for changes.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── Users ────────────────────────────────────────────────────────────────────
CREATE TABLE app_users (
    id          VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    full_name   VARCHAR(100) NOT NULL,
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

-- ── Wallets ──────────────────────────────────────────────────────────────────
CREATE TABLE wallets (
    id           VARCHAR(36)      PRIMARY KEY DEFAULT gen_random_uuid()::text,
    owner_name   VARCHAR(100)     NOT NULL,
    -- NUMERIC(19,4) gives us 15 digits before the decimal, 4 after.
    -- Never use FLOAT or DOUBLE for money in a database.
    balance      NUMERIC(19, 4)   NOT NULL DEFAULT 0.0000,
    version      BIGINT           NOT NULL DEFAULT 0,
    created_at   TIMESTAMP        NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP        NOT NULL DEFAULT now(),
    CONSTRAINT wallets_balance_non_negative CHECK (balance >= 0)
);

-- ── Transactions (append-only ledger) ────────────────────────────────────────
CREATE TABLE transactions (
    id             VARCHAR(36)    PRIMARY KEY DEFAULT gen_random_uuid()::text,
    wallet_id      VARCHAR(36)    NOT NULL REFERENCES wallets(id),
    reference_id   VARCHAR(36),   -- links the two legs of a transfer
    type           VARCHAR(20)    NOT NULL,
    amount         NUMERIC(19, 4) NOT NULL,
    balance_after  NUMERIC(19, 4) NOT NULL,
    description    VARCHAR(255),
    created_at     TIMESTAMP      NOT NULL DEFAULT now(),
    CONSTRAINT transactions_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_transactions_wallet_id  ON transactions(wallet_id);
CREATE INDEX idx_transactions_created_at ON transactions(created_at DESC);
CREATE INDEX idx_transactions_reference  ON transactions(reference_id) WHERE reference_id IS NOT NULL;
