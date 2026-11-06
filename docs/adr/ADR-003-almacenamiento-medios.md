# ADR-003 — Almacenamiento de medios

**Estado:** aceptada · **Fecha:** 2026-11-06
**Nota:** la fase 1 usa disco local detrás del mismo `ObjectStoragePort` — ver ADR-008.

## Contexto
Las imágenes son el mayor volumen. Guardarlas en PostgreSQL es un antipatrón clásico.

## Decisión
1. **S3-compatible desde el día 1** con el SDK de AWS v2 (`software.amazon.awssdk:s3` 2.57.10),
   apuntando a **Cloudflare R2** o Backblaze B2 (egress 0).
2. **Subida directa** con URL prefirmada: el servidor no proxya binarios.
3. **Commit con revalidación**: magic bytes, re-encode, EXIF/GPS fuera, sha256.
4. **Claves content-addressed**: `media/{sha256[0:2]}/{sha256}.{ext}`.
5. **Derivados en el edge**, no en disco.
6. `altText` obligatorio.

## Alternativas
| Alternativa | Por qué no |
|---|---|
| **MinIO community** | Repo marcado como no mantenido (feb 2026) y solo lectura (abril 2026); AIStor exige licencia |
| Bytes en PostgreSQL | Hincha backups y arruina el rendimiento |
| Disco local + nginx | Se pierde al escalar y no da CDN |
| Cloudflare Images como almacén | Cómodo pero lock-in fuerte de transformación |

## Consecuencias
- Positivas: independencia del proveedor (misma API en R2/S3/B2), dedupe gratis, caché inmutable.
- Negativas: hay que gestionar CORS del bucket y la política de vida.
