package com.noticiasplaneta.identity.adapter.in.security;

import com.noticiasplaneta.identity.application.AdminAuthenticator;
import com.noticiasplaneta.identity.domain.Account;
import com.noticiasplaneta.shared.domain.DomainException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Login del panel: email + contraseña + TOTP en un solo POST.
 *
 * <p>Se hace a mano en vez de con {@code formLogin} porque el TOTP tiene que validarse
 * <em>antes</em> de crear la sesión, y eso no encaja en el filtro estándar sin añadir
 * fuentes de detalles y proveedores que solo añaden ceremonia.
 */
public class AdminLoginFilter extends OncePerRequestFilter {

    private final AdminAuthenticator authenticator;
    private final HttpSessionSecurityContextRepository contextRepository =
            new HttpSessionSecurityContextRepository();

    public AdminLoginFilter(AdminAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod())
                && "/admin/login".equals(pathWithinApp(request)));
    }

    /** getServletPath() no es fiable fuera de un contenedor real; se calcula desde la URI. */
    private static String pathWithinApp(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return (context != null && !context.isEmpty() && uri.startsWith(context))
                ? uri.substring(context.length())
                : uri;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        String totp = request.getParameter("totp");

        try {
            Account account = authenticator.authenticate(email, password, totp);

            var authentication = new UsernamePasswordAuthenticationToken(
                    account.email(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + account.role().name())));

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            HttpSession session = request.getSession(true);
            session.setAttribute("accountId", account.id().toString());
            session.setAttribute("displayName", account.displayName());
            contextRepository.saveContext(context, request, response);

            response.sendRedirect("/admin");
        } catch (DomainException e) {
            response.sendRedirect("/admin/login?error=" + e.code().toLowerCase());
        }
    }
}
