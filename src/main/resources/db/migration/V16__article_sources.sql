-- F04 · Fuentes vinculadas y contraste
--
-- Se guarda una fila por URL y artículo. El contraste cuenta dominios raíz
-- distintos; las fuentes caídas o con peso 0 no suman.

create table article_source (
    id           uuid primary key,
    article_id   uuid        not null references article (id) on delete cascade,
    url          text        not null,
    root_domain  text        not null,
    kind         text        not null,
    http_status  integer,
    checked_at   timestamptz,
    weight       integer     not null default 1,
    unreachable  boolean     not null default false,
    created_at   timestamptz not null default now(),
    unique (article_id, url)
);

create index idx_article_source_article on article_source (article_id);

alter table article add column independent_source_count integer not null default 0;
alter table article add column contrast_level text not null default 'LOW';
