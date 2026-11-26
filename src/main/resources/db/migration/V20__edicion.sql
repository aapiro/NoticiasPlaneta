-- F7 · Panel de estado editorial y control de la edición
--
-- Cada ejecución de la edición (desde el panel o desde scripts/edicion-diaria.sh)
-- deja aquí su informe: cuándo, quién, cuántas fuentes, cuántas piezas creadas,
-- cuántas publicadas y qué falló. El panel lee de esta tabla; no adivina cifras
-- contando artículos a posteriori, porque eso no distingue una ejecución de otra.

create table edicion_ejecucion (
    id                  uuid        primary key,
    iniciada_en         timestamptz not null,
    terminada_en        timestamptz,
    actor               text        not null,
    modo                text        not null,
    estado              text        not null,
    fuentes             integer     not null default 0,
    fuentes_con_error   integer     not null default 0,
    creadas             integer     not null default 0,
    borradores          integer     not null default 0,
    publicadas          integer     not null default 0,
    errores             integer     not null default 0,
    detalle             text,
    constraint ck_edicion_modo check (modo in ('publicar', 'borrador')),
    constraint ck_edicion_estado check (estado in ('EN_CURSO', 'COMPLETADA', 'PARCIAL', 'FALLIDA'))
);

comment on column edicion_ejecucion.fuentes is
    'Fuentes consultadas en esta ejecución (ACTIVE y DEGRADED; nunca DISABLED).';
comment on column edicion_ejecucion.creadas is
    'Items nuevos que trajo la ingesta en esta ejecución.';
comment on column edicion_ejecucion.borradores is
    'Borradores creados a partir de esos items en esta ejecución.';
comment on column edicion_ejecucion.publicadas is
    'Piezas publicadas por esta ejecución (0 si el modo fue borrador).';

-- Candado de una sola ejecución a la vez, en la propia base de datos: dos clics
-- simultáneos en «ejecutar ahora» no pueden solaparse ni en dos instancias.
create unique index uq_edicion_en_curso on edicion_ejecucion (estado) where estado = 'EN_CURSO';

create index ix_edicion_ejecucion_iniciada on edicion_ejecucion (iniciada_en desc);

-- Qué piezas publicó cada ejecución: el enlace a «la edición generada».
create table edicion_ejecucion_pieza (
    ejecucion_id uuid not null references edicion_ejecucion (id) on delete cascade,
    article_id   uuid not null,
    section_id   text not null,
    titulo       text not null,
    posicion     integer not null default 0,
    primary key (ejecucion_id, article_id)
);

comment on table edicion_ejecucion_pieza is
    'Piezas que entraron en la edición de una ejecución, en orden de aparición.';
