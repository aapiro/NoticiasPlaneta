-- ADR-011 · Capacidades de IA activables
-- Fuente de verdad en caliente: manda sobre application.yml.

create table feature_flag (
    capability  text primary key,
    mode        text        not null default 'OFF',
    updated_by  text,
    updated_at  timestamptz not null default now(),
    constraint ck_feature_flag_mode check (mode in ('OFF', 'LOCAL', 'EXTERNAL', 'HYBRID'))
);

insert into feature_flag (capability, mode) values
    ('SEARCH_SEMANTIC', 'OFF'),
    ('ENRICH_TAGS', 'OFF'),
    ('ENRICH_ENTITIES', 'OFF'),
    ('ENRICH_SUMMARY', 'OFF'),
    ('ARTICLE_RELATED', 'OFF'),
    ('PUBLISH_AUTO', 'OFF')
on conflict (capability) do nothing;
