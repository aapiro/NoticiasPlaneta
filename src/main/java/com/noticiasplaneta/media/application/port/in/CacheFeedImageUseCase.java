package com.noticiasplaneta.media.application.port.in;

import java.util.Optional;

/**
 * Cachea en casa la foto que el feed enlaza (backlog #2).
 *
 * <p>Hoy la portada hace hotlink a El País, ABC y elDiario. Si cambian la URL,
 * bloquean el referer o caduca la firma, la portada y las vistas previas se quedan
 * sin imagen. Aquí se descarga una vez, se limpia y se sirve desde {@code /media/}.
 */
public interface CacheFeedImageUseCase {

    /**
     * Descarga, procesa y adjunta como imagen principal del artículo.
     *
     * @param imageUrl  URL de origen (la del feed)
     * @param articleId artículo al que se vincula
     * @param altText   texto alternativo; si falta se usa un genérico
     * @return id del medio cacheado, o vacío si no se pudo (URL rota, no es imagen,
     *         demasiado grande). Nunca lanza por un fallo de red: la noticia sigue
     *         siendo válida sin foto.
     */
    Optional<String> cacheFromUrl(String imageUrl, String articleId, String altText);
}
