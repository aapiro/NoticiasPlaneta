package com.noticiasplaneta.identity.adapter.in.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Segunda capa para el panel: HTTP Basic delante del login con TOTP.
 *
 * <p>ADR-010 pide que el panel no esté expuesto hasta que haya passkeys. Mientras tanto,
 * quien escanee {@code /admin} se come un 401 sin ver siquiera el formulario, y el TOTP
 * queda detrás.
 *
 * <p>Sin {@code ADMIN_BASIC_USER} y {@code ADMIN_BASIC_PASSWORD} el filtro no hace nada:
 * en local no estorba, y olvidarse de las variables no deja el panel abierto por
 * accidente... siempre que se configuren en producción. Si están, exige la cabecera.
 */
@Component
public class AdminBasicAuthFilter extends OncePerRequestFilter {

    private final byte[] esperado;

    public AdminBasicAuthFilter(@Value("${admin.basic.user:}") String usuario,
                                @Value("${admin.basic.password:}") String contrasena) {
        this.esperado = (usuario.isBlank() || contrasena.isBlank())
                ? null
                : ("Basic " + Base64.getEncoder().encodeToString(
                        (usuario + ":" + contrasena).getBytes(StandardCharsets.UTF_8)))
                        .getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (esperado == null) {
            chain.doFilter(request, response);
            return;
        }

        String cabecera = request.getHeader("Authorization");
        if (cabecera != null && cabecera.regionMatches(true, 0, "Basic ", 0, 6)) {
            byte[] recibido = cabecera.getBytes(StandardCharsets.UTF_8);
            // Comparación en tiempo constante: no filtrar por prefijo cuánto acierta.
            if (MessageDigest.isEqual(esperado, recibido)) {
                chain.doFilter(request, response);
                return;
            }
        }

        response.setHeader("WWW-Authenticate", "Basic realm=\"Noticias Planeta\", charset=\"UTF-8\"");
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    /** Solo el panel. La portada, el feed y los medios son públicos. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/admin");
    }
}
