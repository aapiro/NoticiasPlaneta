-- Fotos en las noticias: qué medios usa un artículo y en qué papel.
--
-- Antes existían medios subidos que ningún artículo podía usar.

create table article_media (
    article_id uuid not null references article (id) on delete cascade,
    media_id   uuid not null references media_asset (id),
    role       text not null,
    position   int  not null default 0,
    caption    text,
    primary key (article_id, media_id)
);

-- Una sola imagen principal por artículo: el resto van en el cuerpo.
create unique index uq_article_media_lead on article_media (article_id) where role = 'LEAD';
create index idx_article_media_article on article_media (article_id, position);
