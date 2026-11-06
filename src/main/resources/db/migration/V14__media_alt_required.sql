-- El texto alternativo deja de ser opcional.
--
-- Había medios guardados con alt_text nulo: la galería mostraba "null" y la imagen
-- no existía para quien usa lector de pantalla. No se borra nada: a los históricos
-- se les pone un texto explícito para que se puedan localizar y corregir.

update media_asset
set alt_text = '(sin descripción: pendiente de rellenar)'
where alt_text is null or length(trim(alt_text)) < 8;

alter table media_asset
    alter column alt_text set not null;

alter table media_asset
    add constraint ck_media_asset_alt_length check (length(trim(alt_text)) >= 8);
