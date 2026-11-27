# Copias de seguridad y restauración

Backlog #8. Un volcado que nunca se ha restaurado no es una copia de seguridad: es un
fichero con buena pinta. Por eso el script **restaura cada copia en una base temporal y
compara el número de artículos** antes de darla por buena.

## Uso diario

```bash
./scripts/backup.sh
```

```
volcando noticias desde noticiasplaneta-db-1
  volcado: backups/noticias-20261127-211834.dump (7.0M)
  verificando: restaurando en noticias_verificacion
  verificado: 4763 artículos restaurados, idénticos al origen
  copias guardadas: 1
OK backups/noticias-20261127-211834.dump
```

Si la verificación falla, el script sale con error y **la copia no se da por buena**.
Eso es lo que se quiere de una copia: que grite mientras hay tiempo.

## Variables de entorno

| Variable | Por defecto | Para qué |
|---|---|---|
| `BACKUP_DIR` | `backups` | Dónde se guardan |
| `BACKUP_RETENCION_DIAS` | `14` | Se borran las más viejas |
| `DB_CONTAINER` | `noticiasplaneta-db-1` | Contenedor de Postgres |
| `DB_USER` / `DB_NAME` | `noticias` / `noticias` | Credenciales |

Para el cron de producción, apuntar `BACKUP_DIR` a un disco distinto del de los datos.
Una copia en el mismo volumen que la base se borra con la base.

## Restaurar

```bash
./scripts/backup.sh --listar
./scripts/backup.sh --restaurar backups/noticias-20261127-211834.dump
```

Pide escribir `RESTAURAR` en mayúsculas. Antes de preguntar comprueba que el fichero es
un volcado legible: no tiene sentido pedir confirmación por algo ya roto.

La restauración **reemplaza** el contenido: borra el esquema `public` y lo vuelve a
crear desde el volcado.

## Por qué formato custom (`-Fc`)

- Comprimido: 7 MB frente a ~40 MB en SQL plano.
- `pg_restore -l` permite validar el fichero **sin restaurarlo**.
- Se puede restaurar una sola tabla (`-t article`) para recuperar un dato suelto.

## Lo que esta copia no cubre

- **El volumen de imágenes** (`media.root`). El volcado guarda las rutas en
  `media_asset`, pero los ficheros están en el volumen. Una restauración en otra
  máquina sirve las noticias con la foto del feed hasta que se copie el volumen.
- **Los secretos** (`.env`). Van aparte y a mano.
- **La configuración de Caddy**, si se cambia a mano y no está en el repo.
