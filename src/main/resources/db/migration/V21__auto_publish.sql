-- F6 · Sección automatizada con IA (PUBLISH_AUTO)
--
-- Tres piezas de estado que antes no existían:
--   1. De qué fuentes se fía el sistema para publicar solo (`source.confianza`).
--   2. Cupo diario propio de la automatización por sección.
--   3. Traza de lo que hizo la máquina: qué modelo clasificó cada pieza y con qué
--      enfoque se publicó (F6 + ADR-009 + ADR-012).

-- 1. Fuente de confianza. Apagado por defecto: hasta que un editor la marque, esa
--    fuente no publica sola. El fallo por defecto es no publicar, no publicar de más.
alter table source add column confianza boolean not null default false;

comment on column source.confianza is
    'Si es true, sus piezas pueden publicarse automaticamente (F6). Apagado por defecto.';

create index idx_source_confianza on source (confianza) where confianza;

-- 2. Cupo diario de la automatización, aparte del cupo de la edición manual.
alter table section_config add column auto_limite_diario integer not null default 3;

alter table section_config add constraint ck_section_auto_limite
    check (auto_limite_diario between 0 and 24);

comment on column section_config.auto_limite_diario is
    'Maximo de piezas que la seccion puede publicar sola por dia natural (F6). 0 = ninguna.';

-- 3a. Clasificación por modelo: qué dijo el modelo de cada borrador y cuándo.
--     Se guarda aparte del artículo para no reescribir el agregado editorial y
--     para no volver a pagar la clasificación de la misma pieza en cada pasada.
create table article_auto_classification (
    article_id     uuid primary key references article (id),
    seccion        text             not null,
    modelo         text             not null,
    prompt_version text             not null,
    confianza      numeric(3, 2)    not null,
    clasificada_en timestamptz      not null default now(),
    constraint ck_auto_class_confianza check (confianza between 0 and 1)
);

-- 3b. Traza de publicación automática: la marca del artículo y las reglas que pasó.
create table article_auto_publicacion (
    article_id     uuid primary key references article (id),
    seccion        text        not null,
    enfoque        text        not null,
    modelo         text        not null,
    prompt_version text        not null,
    reglas         text[]      not null default '{}',
    publicado_en   timestamptz not null default now()
);

create index idx_auto_publicacion_dia on article_auto_publicacion (seccion, publicado_en desc);

comment on table article_auto_publicacion is
    'Una fila por pieza publicada por la maquina. Es la marca que se muestra al lector y la que lee audit_log.';
