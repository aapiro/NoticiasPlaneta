-- Limpieza de los datos que generaron las pruebas de desarrollo.
--
-- NO BORRA NADA. Archiva con motivo explícito: la portada, el buscador y las páginas
-- públicas dejan de mostrarlos y el registro queda intacto.
--
-- CRITERIO: origin = 'MANUAL'. Todos los artículos que se crearon a mano por la API
-- durante las pruebas del entorno de desarrollo. Los 313 de la ingesta (origin =
-- 'INGEST') son noticias reales y no se tocan.
--
-- No se filtra por título a propósito: un filtro por palabras como "prueba" archiva
-- noticias auténticas ("...las pruebas de que el PP pagó su defensa").
--
-- Uso:  psql -U noticias -d noticias -f scripts/limpiar-datos-de-prueba.sql
--       (revisa el bloque 1 y descomenta el bloque 2 para ejecutar)

-- 1 · Qué se va a tocar
select id, state, left(title, 60) as titular
from article
where origin = 'MANUAL' and state <> 'ARCHIVED'
order by state, title;

-- 2 · Archivado (descomenta para ejecutar)
-- begin;
--
-- update article
-- set state = 'ARCHIVED',
--     unpublished_at = now(),
--     unpublish_reason = 'Artículo de prueba del entorno de desarrollo'
-- where origin = 'MANUAL' and state <> 'ARCHIVED';
--
-- insert into audit_log (actor_id, actor_type, action, entity, entity_id, before_state, after_state, at)
-- select 'script-limpieza', 'SYSTEM', 'ARTICLE_RETIRADA', 'Article', id::text, 'PUBLISHED', 'ARCHIVED', now()
-- from article
-- where origin = 'MANUAL'
--   and unpublish_reason = 'Artículo de prueba del entorno de desarrollo'
--   and state = 'ARCHIVED';
--
-- commit;

-- 3 · Medios con el texto alternativo pendiente
--    No los borres: reescribe su alt con lo que de verdad muestra la imagen.
select id, storage_key, alt_text
from media_asset
where alt_text like '(sin descripción%';
