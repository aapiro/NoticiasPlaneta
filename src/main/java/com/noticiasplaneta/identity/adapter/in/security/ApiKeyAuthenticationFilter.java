package com.noticiasplaneta.identity.adapter.in.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Autentica agentes por cabecera {@code X-Api-Key} con comparación en tiempo constante.
 * El principal es el {@code agentId}, que después acaba en {@code audit_log}.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Api-Key";

    private final SecurityProperties properties;

    public ApiKeyAuthenticationFilter(SecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);

        if (provided != null && properties.apiKeyEnabled()
                && MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8),
                properties.apiKey().getBytes(StandardCharsets.UTF_8))) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    properties.agentId(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_AGENT")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        chain.doFilter(request, response);
    }
}
