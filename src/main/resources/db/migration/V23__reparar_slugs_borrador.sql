-- F6 · Reparación de slugs de borrador inválidos.
--
-- El script de la edición manual construía el slug truncando el titular a 55 caracteres
-- y añadiendo el id. El corte podía dejar el slug acabado en guion ('...-inmobiliario-de--a070')
-- o, con titulares sin letras latinas, vacío. Al rehidratar el agregado Article eso
-- lanzaba SLUG_INVALID y cualquier lectura de la pieza daba 500; con la publicación
-- automática, una sola fila así detenía la pasada entera.
--
-- Los publicados NO se tocan: su URL es canónica (ADR-001) y cambiarla rompería enlaces.
-- Los borradores sí, antes de que salgan a la calle.

update article
set slug = left(
               coalesce(
                   nullif(
                       trim(both '-' from
                            regexp_replace(
                                regexp_replace(lower(slug), '-{2,}', '-', 'g'),
                                '[^a-z0-9-]', '', 'g')),
                       ''),
                   'noticia'),
               60)
           || '-' || left(id::text, 8)
where state = 'DRAFT'
  and slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$';

comment on column article.slug is
    'URL canonica. Inmutable desde la primera publicacion (ADR-001); los borradores pueden repararse.';
