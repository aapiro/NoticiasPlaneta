-- ADR-012 · Secciones configurables con enfoque editorial
-- Las secciones dejan de ser texto libre en article.section_id: pasan a estar
-- gobernadas por esta tabla (activa/desactiva, orden, cupo y enfoque).

create table section_config (
    slug                   text primary key,
    nombre                 text        not null,
    descripcion            text,
    activa                 boolean     not null default true,
    orden                  integer     not null default 100,
    max_piezas_por_edicion integer     not null default 3,
    enfoque                text        not null default 'NEUTRAL',
    auto_publica           boolean     not null default false,
    creada_en              timestamptz not null default now(),
    actualizada_en         timestamptz not null default now(),
    constraint ck_section_enfoque check (enfoque in ('IZQUIERDA', 'DERECHA', 'CENTRO', 'NEUTRAL')),
    constraint ck_section_cupo check (max_piezas_por_edicion between 0 and 50)
);

comment on column section_config.activa is
    'Si esta en false la seccion no aparece en la navegacion ni recibe piezas nuevas.';
comment on column section_config.enfoque is
    'Linea editorial declarada de la seccion (ADR-012). Se muestra en el portal.';
comment on column section_config.auto_publica is
    'Si esta en true la seccion puede publicar sin intervencion humana (F6).';

-- Semilla: las nuevas primero, y las que ya existen para no romper lo publicado.
insert into section_config (slug, nombre, descripcion, activa, orden, max_piezas_por_edicion, enfoque) values
    ('tecnologia', 'Tecnología', 'Inteligencia artificial, plataformas, ciberseguridad y ciencia aplicada.', true, 10, 3, 'NEUTRAL'),
    ('politica',   'Política',   'Gobierno, parlamento, partidos y elecciones.',                          true, 20, 3, 'NEUTRAL'),
    ('espana',     'España',     'Actualidad nacional.',                                                  true, 30, 3, 'NEUTRAL'),
    ('economia',   'Economía',   'Macroeconomía, empresas y mercados.',                                   true, 40, 3, 'NEUTRAL'),
    ('mundo',      'Mundo',      'Actualidad internacional.',                                             true, 50, 3, 'NEUTRAL'),
    ('deportes',   'Deportes',   'Competiciones y resultados.',                                           true, 60, 3, 'NEUTRAL'),
    ('general',    'General',    'Sección por defecto de la ingesta sin clasificar.',                     true, 90, 3, 'NEUTRAL')
on conflict (slug) do nothing;

-- Con qué enfoque se escribió cada artículo (ADR-012, punto 3: auditable).
alter table article add column editorial_bias text;
alter table article add constraint ck_article_bias
    check (editorial_bias is null or editorial_bias in ('IZQUIERDA', 'DERECHA', 'CENTRO', 'NEUTRAL'));

comment on column article.editorial_bias is
    'Enfoque editorial aplicado al escribir la pieza. Nulo si se ingirió sin redacción.';
