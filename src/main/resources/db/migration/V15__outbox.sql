-- F06 · Outbox para los efectos de publicación.
-- El estado del artículo y el evento se escriben en la misma transacción;
-- un relay idempotente procesa después caché e índice.

create table outbox (
    id           uuid primary key,
    aggregate_id uuid        not null,
    event_type   text        not null,
    payload      jsonb       not null,
    created_at   timestamptz not null default now(),
    processed_at timestamptz,
    attempts     integer     not null default 0,
    last_error   text
);

create index idx_outbox_pending on outbox (created_at) where processed_at is null;
