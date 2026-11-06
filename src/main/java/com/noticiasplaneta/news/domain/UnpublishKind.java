package com.noticiasplaneta.news.domain;

/** Por qué se retira o se corrige un artículo. */
public enum UnpublishKind {
    /** Se retira del público. La URL responde 410 durante 90 días. */
    RETIRADA,
    /** Se mantiene publicado, con nota visible de lo corregido. */
    CORRECCION,
    /** Corrección formal con rectificación pública. */
    RECTIFICACION
}
