-- Un feed no puede estar registrado dos veces: si lo está, cada noticia entra
-- multiplicada y se crean borradores duplicados.
--
-- No se borra nada: los duplicados se marcan DISABLED y se conserva el más antiguo
-- activo. Las noticias ya ingestadas siguen ahí para poder auditarlas.

with ordenadas as (
    select id, feed_url, row_number() over (partition by feed_url order by id) as rn
    from source
    where state <> 'DISABLED'
)
update source s
set state = 'DISABLED'
from ordenadas o
where s.id = o.id and o.rn > 1;

create unique index uq_source_feed_url_active
    on source (feed_url)
    where state <> 'DISABLED';
