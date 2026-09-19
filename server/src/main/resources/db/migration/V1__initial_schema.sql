CREATE TABLE currency_pairs (
    id BIGSERIAL PRIMARY KEY,
    source_pair_id INTEGER NOT NULL UNIQUE,
    symbol VARCHAR(16) NOT NULL UNIQUE,
    display_order INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE swap_points (
    id BIGSERIAL PRIMARY KEY,
    currency_pair_id BIGINT NOT NULL REFERENCES currency_pairs(id),
    trade_date DATE NOT NULL,
    sp_days INTEGER NOT NULL CHECK (sp_days >= 0),
    buy_swap NUMERIC(12, 1),
    sell_swap NUMERIC(12, 1),
    publication_state VARCHAR(16) NOT NULL,
    source_month CHAR(6) NOT NULL,
    first_fetched_at TIMESTAMPTZ NOT NULL,
    last_fetched_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(currency_pair_id, trade_date)
);

CREATE INDEX swap_points_month_idx ON swap_points(source_month);

CREATE TABLE fetch_runs (
    id UUID PRIMARY KEY,
    source_month CHAR(6) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    status VARCHAR(16) NOT NULL,
    http_status INTEGER,
    record_count INTEGER NOT NULL DEFAULT 0,
    content_hash VARCHAR(64),
    error_code VARCHAR(64),
    error_message TEXT
);

