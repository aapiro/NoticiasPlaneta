-- F01 · Ingesta de fuentes

create table source (
    id              uuid primary key,
    name            text        not null,
    feed_url        text        not null,
    state           text        not null,
    failure_count   integer     not null default 0,
    last_checked_at timestamptz,
    etag            text,
    last_modified   text
);

create table ingest_item (
    id            uuid primary key,
    source_id     uuid        not null references source (id),
    external_id   text        not null,
    content_hash  text        not null,
    title         text        not null,
    body          text        not null,
    canonical_url text        not null,
    published_at  timestamptz,
    author        text,
    created_at    timestamptz not null default now(),
    constraint uq_ingest_item_source_external unique (source_id, external_id)
);

create index idx_ingest_item_content_hash on ingest_item (source_id, content_hash);
create index idx_ingest_item_created on ingest_item (created_at desc);
