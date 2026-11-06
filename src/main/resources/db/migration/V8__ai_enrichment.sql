-- F03 · Enriquecido asíncrono

alter table article add column body text not null default '';

create table ai_job (
    id           uuid primary key,
    type         text        not null,
    article_id   uuid        not null,
    state        text        not null,
    attempts     integer     not null default 0,
    last_error   text,
    cost_micros  bigint      not null default 0,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);

create index idx_ai_job_state on ai_job (state, created_at);
create unique index uq_ai_job_article_type on ai_job (article_id, type);

create table article_enrichment (
    article_id     uuid primary key references article (id),
    tags           text[]      not null default '{}',
    entities       jsonb       not null default '[]'::jsonb,
    summary        text,
    model          text        not null,
    prompt_version text        not null,
    provider       text        not null,
    created_at     timestamptz not null default now()
);
