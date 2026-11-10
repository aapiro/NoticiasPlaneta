-- Reencola el enriquecido de los artículos publicados.
--
-- Cuándo usarlo:
--   1. Poner DEEPSEEK_API_KEY en .env y recrear la app.
--   2. Ejecutar este script:  make backfill-ai
--
-- Qué hace:
--   - Devuelve a PENDING los jobs fallidos de artículos que siguen publicados.
--   - Encola los publicados que nunca tuvieron job (por ejemplo, anteriores a F03).
--   - No toca jobs de artículos archivados.
--
-- Es idempotente: se puede ejecutar varias veces sin duplicar jobs.

begin;

update ai_job j
set state = 'PENDING',
    attempts = 0,
    last_error = null,
    updated_at = now()
from article a
where a.id = j.article_id
  and a.state = 'PUBLISHED'
  and j.state = 'FAILED';

insert into ai_job (id, type, article_id, state, attempts, cost_micros, created_at, updated_at)
select gen_random_uuid(), 'enrich', a.id, 'PENDING', 0, 0, now(), now()
from article a
where a.state = 'PUBLISHED'
  and not exists (
      select 1
      from ai_job j
      where j.article_id = a.id
        and j.type = 'enrich'
  );

commit;

-- Verificación rápida
select state, count(*) as jobs
from ai_job
group by state
order by state;

select count(*) as articulos_publicados,
       count(*) filter (where embedding is not null) as con_embedding
from article
where state = 'PUBLISHED';
