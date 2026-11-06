-- F02 + F05 · Artículos, revisiones y auditoría

create table article (
    id             uuid primary key,
    language       text        not null,
    section_id     text        not null,
    origin         text        not null,
    ingest_item_id text,
    slug           text        not null,
    title          text        not null,
    state          text        not null,
    revision_count integer     not null default 1,
    published_at   timestamptz,
    scheduled_at   timestamptz,
    created_at     timestamptz not null default now(),
    constraint uq_article_slug_language unique (slug, language)
);

create unique index uq_article_ingest_item on article (ingest_item_id) where ingest_item_id is not null;
create index idx_article_state on article (state, created_at desc);

create table audit_log (
    id           bigserial primary key,
    actor_id     text        not null,
    actor_type   text        not null,
    action       text        not null,
    entity       text        not null,
    entity_id    text,
    before_state text,
    after_state  text,
    at           timestamptz not null
);

create index idx_audit_log_entity on audit_log (entity, entity_id, at desc);
create index idx_audit_log_actor on audit_log (actor_id, at desc);
