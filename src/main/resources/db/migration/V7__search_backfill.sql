-- F10 · Relleno del índice léxico para artículos anteriores al trigger.
-- El trigger solo actúa en insert o en update de title; las filas que ya existían
-- cuando se creó V6 se quedaron con search_vector nulo y no aparecían en las búsquedas.

update article
set search_vector = setweight(to_tsvector('spanish', coalesce(title, '')), 'A')
where search_vector is null;
