-- Historial de versiones del texto.
--
-- Cada edición guarda cómo estaba el artículo ANTES de cambiarlo, así que el
-- historial siempre permite volver al original. Nunca se borra una revisión.

create table article_revision (
    id          bigserial primary key,
    article_id  uuid not null references article (id) on delete cascade,
    revision    int  not null,
    title       text not null,
    body        text not null,
    edited_by   text not null,
    edited_at   timestamptz not null,
    note        text,
    unique (article_id, revision)
);

create index idx_article_revision_article on article_revision (article_id, revision desc);
