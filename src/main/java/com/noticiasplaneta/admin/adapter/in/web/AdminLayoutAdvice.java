package com.noticiasplaneta.admin.adapter.in.web;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Datos que el layout del panel necesita en todas sus páginas.
 *
 * <p>{@code esRaiz} decide si se enseña el chip de Cuentas. No es la seguridad
 * —esa la pone {@code SecurityConfiguration} con {@code hasRole('ADMIN')}— pero
 * evita ofrecer un enlace que devolvería 403.
 */
@ControllerAdvice(basePackages = "com.noticiasplaneta.admin")
class AdminLayoutAdvice {

    @ModelAttribute("esRaiz")
    boolean esRaiz(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
