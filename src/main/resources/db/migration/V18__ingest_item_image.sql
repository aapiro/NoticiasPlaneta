-- ADR-012 · Fotos de las noticias
-- La imagen principal llega del feed (Media RSS). Se guarda en el item de ingesta;
-- la portada la saca con un join, sin duplicarla en article.

alter table ingest_item add column image_url text;

comment on column ingest_item.image_url is
    'URL de origen de la foto principal del feed (media:content). Puede ser nula.';
