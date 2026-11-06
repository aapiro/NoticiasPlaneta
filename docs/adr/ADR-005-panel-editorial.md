# ADR-005 — Panel editorial propio en el monolito

**Estado:** aceptada · **Fecha:** 2026-11-06

## Decisión
Módulo `admin` con **Thymeleaf + htmx** dentro de la misma aplicación, no un CMS headless
ni una SPA separada.

## Motivos
- El flujo editorial (draft → review → scheduled → published → archived) es nuestro y es simple.
- Un CMS headless obliga a modelar el negocio en su idioma y a sincronizar dos fuentes de verdad.
- Una SPA duplica stack, despliegue y sesión para escribir titulares.

## Alcance del MVP
Editor con autosave (10 s) y recuperación local · versiones inmutables con diff y rollback ·
preview con token firmado y caducidad · programación de publicación · SEO por artículo ·
biblioteca de medios con alt obligatorio · kill switch con purga de CDN · auditoría visible.

## Fuera del MVP
Comentarios con moderación, analítica propia, paywall, multiidioma.

## Consecuencias
- Positivas: cero integraciones, cero sincronización, mismo dominio y mismas reglas.
- Negativas: hay que construir la UX editorial (≈3 semanas). Se asume.
