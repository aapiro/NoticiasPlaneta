-- F08 · Activos de medios

create table media_asset (
    id          uuid primary key,
    sha256      text        not null,
    storage_key text        not null,
    mime        text        not null,
    width       integer     not null,
    height      integer     not null,
    size_bytes  bigint      not null,
    uploader_id text        not null,
    created_at  timestamptz not null default now(),
    alt_text    text,
    credit      text
);

create unique index uq_media_asset_sha256 on media_asset (sha256);
create index idx_media_asset_created on media_asset (created_at desc);
