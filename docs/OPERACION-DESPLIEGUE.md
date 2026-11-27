# Despliegue en producción

Última verificación: 27/11/2026.

| | |
|---|---|
| URL | https://noticiasplaneta.com |
| Servidor | Dokploy (`https://vukum.com`) |
| Proyecto | `Noticias Planeta` — `4M0Vg6979WsjwzI_YOZr_` |
| Entorno | `production` — `tV0Y8qGciLxnG_4W27PXV` |
| Compose | `noticias-planeta` — `4JMAZC2QI7zivDHHzV7eE` |
| Fichero | `docker-compose.dokploy.yml` |
| Certificado | Let's Encrypt, renovación automática |

## Lo que hay corriendo

```
compose-calculate-virtual-driver-mrk1wj-db-1    healthy
compose-calculate-virtual-driver-mrk1wj-app-1   healthy
```

El nombre del stack lo genera Dokploy (`appName`), no el que se pide. No confundirlo
con el nombre del compose, que sí es `noticias-planeta`.

## Trampa: `sourceType` tiene que ser `git`, no `github`

El primer despliegue falló con `composeStatus: error` y sin logs accesibles por API.
La causa: con `sourceType: "github"` Dokploy usa la GitHub App, que **no está
instalada** en esta instancia. El repo es público, así que no hace falta.

Lo que funciona:

```json
{
  "composeId": "4JMAZC2QI7zivDHHzV7eE",
  "sourceType": "git",
  "customGitUrl": "https://github.com/aapiro/NoticiasPlaneta.git",
  "customGitBranch": "master",
  "composePath": "docker-compose.dokploy.yml"
}
```

Ojo con los nombres de campo: `gitUrl` y `repository` se aceptan sin error pero **no
se guardan** (queda `customGitUrl: null` y el clone falla). Y `customGitSSHKeyId: ""`
devuelve **500**.

## Variables de entorno

Se cargan en `compose.update` con el campo `env` (texto `CLAVE=valor` por línea).
Las obligatorias, sin valor por defecto en el compose, son `DB_PASSWORD`,
`AGENT_API_KEY` y `DB_PASSWORD` otra vez para la app.

| Variable | Estado |
|---|---|
| `DB_PASSWORD` | generada, solo en Dokploy |
| `AGENT_API_KEY` | generada, solo en Dokploy |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD_HASH`, `ADMIN_TOTP_SECRET` | copiadas del `.env` local |
| `DEEPSEEK_API_KEY` | **vacía** — las capacidades de IA quedan en OFF |
| `RP_ID`, `RP_ORIGIN`, `NOTICIAS_BASE_URL` | `noticiasplaneta.com` |
| `LANGFUSE_ENABLED` | `false` |

## Diferencias con el compose local

- **Sin Caddy**: Dokploy pone Traefik y emite el certificado. El `caddy` local ocupa
  80/443 y chocaría.
- **Sin Ollama**: no hay capacidad LOCAL encendida. Si se enciende `SEARCH_SEMANTIC`
  o `ENRICH_*` en modo `LOCAL`, hay que añadir el servicio (1,6 GB).
- **Sin puertos publicados** en `db`: solo la app habla con la base.

## Estado de la base

Producción arranca **vacía**: las migraciones de Flyway se aplican solas y siembran
`section_config` (7 secciones), pero no hay artículos ni fuentes.

La portada dice «Todavía no hay artículos publicados». Eso es correcto, no un fallo.

Para llevar los datos locales:

```bash
./scripts/backup.sh                                    # volcado local
docker cp backups/noticias-*.dump <contenedor>:/tmp/   # al VPS (necesita SSH)
# y pg_restore allí
```

Las 8 fuentes RSS se vuelven a dar de alta desde `/admin/fuentes`.

## Qué comprobar tras un despliegue

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://noticiasplaneta.com/
curl -s -o /dev/null -w '%{http_code}\n' https://noticiasplaneta.com/feed.xml
curl -s -o /dev/null -w '%{http_code}\n' https://noticiasplaneta.com/robots.txt
curl -s -o /dev/null -w '%{http_code}\n' https://noticiasplaneta.com/sitemap.xml
```

Los cuatro deben dar 200. `/admin` debe dar **302** (sin sesión).
