-- F8 · Trazabilidad del generador de borradores desde un tema
--
-- Una fila por pieza generada. Sin ella no se puede responder «¿de dónde sale este
-- dato?», y una pieza que no se puede auditar no se publica.

create table article_generation (
    article_id             uuid        primary key references article (id) on delete cascade,
    consulta               text        not null,
    seccion                text        not null,
    enfoque                text        not null,
    prompt_version         text        not null,
    modelo                 text,
    proveedor              text,
    coste_micros           bigint      not null default 0,
    fuentes_independientes integer     not null default 0,
    fragmentos             jsonb       not null,
    parrafos               jsonb       not null,
    descartes              jsonb       not null default '[]'::jsonb,
    generado_en            timestamptz not null default now()
);

create index idx_article_generation_seccion on article_generation (seccion, generado_en desc);

comment on table article_generation is
    'F8: consulta, fragmentos (medio/URL/fecha), párrafos con sus citas, descartes y prompt con que se generó un borrador.';
comment on column article_generation.descartes is
    'Párrafos o titulares rechazados por la verificación, con el motivo: un dato sin fuente se elimina, no se suaviza.';
