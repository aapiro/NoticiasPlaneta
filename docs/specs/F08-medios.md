# F08 — Gestión de medios (imágenes y archivos)

**Módulo:** `media` · **Estado:** borrador · **Versión:** 1

## 1. Contexto
Las imágenes son el mayor volumen del sistema. El binario nunca entra en PostgreSQL:
se sube directo al almacén de objetos y el backend solo registra metadatos.

## 2. Actores
- Redactor (navegador)
- Agente `newsroom-redaccion` (adjuntar medios a un borrador)

## 3. Precondiciones
- Cuenta con rol `AUTHOR` o superior.
- Tipo MIME dentro de la lista permitida; tamaño <= 15 MB.

## 4. Flujo principal
1. `POST /admin/media/presign` con `{filename, contentType, size}`.
2. El backend valida tipo y tamaño y devuelve URL prefirmada PUT (caducidad 15 min).
3. El navegador sube el binario **directo** al bucket (el servidor no proxya).
4. `POST /admin/media/commit` con la clave.
5. El backend **descarga el objeto y lo revalida**: magic bytes, decodifica la imagen,
   re-encodea (elimina payloads), quita EXIF/GPS, calcula `sha256`, `width`, `height`.
6. Si `sha256` ya existe → se reutiliza el asset (dedupe).
7. Se persiste `MediaAsset` con `alt_text` obligatorio.
8. Se emite `MediaUploaded.v1`.

## 5. Alternativos y errores
| Código | Situación | Resultado |
|---|---|---|
| E01 | Tipo real distinto del declarado | `415 MIME_MISMATCH`, objeto borrado |
| E02 | Supera 15 MB | `413` |
| E03 | SVG | `415` (XSS almacenado) |
| E04 | Sin `altText` al asociar a artículo publicado | `422 ALT_REQUIRED` |
| E05 | Commit de un objeto inexistente | `404` |

## 6. Reglas
- **RN1** Nunca se confía en la extensión ni en el `Content-Type` del cliente: manda el magic byte.
- **RN2** Toda imagen se re-encodea. Un JPEG que esconde un ZIP no sale del sistema.
- **RN3** EXIF GPS se elimina siempre (protección de fuentes); autor y copyright se conservan.
- **RN4** Clave de objeto = `media/{sha256[0:2]}/{sha256}.{ext}` → inmutable y deduplicable.
- **RN5** `altText` obligatorio para todo asset asociado a contenido publicado.

## 7. Gherkin
```gherkin
@F08 @aceptacion
Escenario: Un archivo con extensión mentida se rechaza
  Dado un archivo .jpg cuyo contenido real es un PDF
  Cuando se hace commit del objeto
  Entonces se rechaza con 415 MIME_MISMATCH
  Y el objeto se elimina del bucket

@F08 @aceptacion
Escenario: La misma imagen subida dos veces se deduplica
  Dado un asset ya registrado con sha256 X
  Cuando se sube otra vez el mismo binario
  Entonces existe un único MediaAsset para X

@F08 @aceptacion
Escenario: No se puede publicar con una imagen sin alt
  Dado un artículo con una imagen sin altText
  Cuando se intenta publicar
  Entonces se rechaza con 422 ALT_REQUIRED
```

## 8. Datos
`media_asset(id, sha256 bytea unique, storage_key, mime, width, height, alt_text, credit, uploader_id, created_at)`

## 9. Puertos
In: `PresignUploadUseCase`, `CommitUploadUseCase`, `McpAttachMediaTool`
Out: `ObjectStoragePort` (presign/put/get/delete) — fase 1: `LocalFilesystemStorageAdapter`; fase 2: S3/R2 sin tocar dominio, `ImageProcessorPort` (re-encode, EXIF), `MediaRepository`

## 10. Eventos
`MediaUploaded.v1 { mediaId, sha256, mime }`

## 11. Tests RED
1. Presign valida tipo y tamaño.
2. Commit con magic byte incorrecto → rechazo y borrado.
3. SVG rechazado.
4. Dedupe por sha256.
5. EXIF GPS eliminado, autor conservado.
6. Alt obligatorio al publicar.

## 12. NFR
Commit < 2 s para imágenes de 5 MB. El binario nunca pasa por memoria de la app más de una vez.
