package com.noticiasplaneta.identity.config;

import com.noticiasplaneta.identity.adapter.in.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

import com.noticiasplaneta.identity.adapter.in.security.AdminLoginFilter;
import com.noticiasplaneta.identity.adapter.in.security.ApiKeyAuthenticationFilter;
import com.noticiasplaneta.identity.application.AdminAuthenticator;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

/**
 * ADR-004: dos superficies, dos cadenas.
 *
 * <ol>
 *   <li>Salud y estáticos: abiertos.</li>
 *   <li>API interna: exige clave de agente o sesión autenticada.</li>
 *   <li>Resto: exige sesión (backoffice).</li>
 * </ol>
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfiguration {

    @Bean
    UserCredentialRepository userCredentialRepository(JdbcTemplate jdbc) {
        return new JdbcUserCredentialRepository(jdbc);
    }

    @Bean
    PublicKeyCredentialUserEntityRepository userEntityRepository(JdbcTemplate jdbc) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbc);
    }

    @Bean
    @Order(1)
    SecurityFilterChain openChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/actuator/health", "/actuator/info")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http, SecurityProperties properties) throws Exception {
        http.securityMatcher("/api/v1/**", "/mcp/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // lectura pública: la portada y el buscador no piden credenciales
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/**").permitAll()
                        .requestMatchers("/mcp/**").hasRole("AGENT")
                        // la URL prefirmada lleva su propia firma HMAC: no necesita sesión
                        .requestMatchers(HttpMethod.PUT, "/api/v1/media/blob/**").permitAll()
                        .anyRequest().hasAnyRole("AGENT", "ADMIN", "CHIEF"))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // sin credenciales → 401 (no 403): es una API, no una página de login
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(new ApiKeyAuthenticationFilter(properties),
                        UsernamePasswordAuthenticationFilter.class)
                .webAuthn(webAuthn -> webAuthn
                        .rpId(properties.rpId())
                        .rpName(properties.rpName())
                        .allowedOrigins(properties.allowedOrigins().toArray(String[]::new)));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain adminChain(HttpSecurity http, AdminAuthenticator authenticator) throws Exception {
        http.securityMatcher("/admin/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        new LoginUrlAuthenticationEntryPoint("/admin/login")))
                .addFilterBefore(new AdminLoginFilter(authenticator),
                        UsernamePasswordAuthenticationFilter.class)
                .logout(l -> l.logoutUrl("/admin/logout").logoutSuccessUrl("/admin/login"))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .csrf(org.springframework.security.config.Customizer.withDefaults());
        return http.build();
    }

    @Bean
    @Order(4)
    SecurityFilterChain webChain(HttpSecurity http, SecurityProperties properties) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        // el sitio público es de solo lectura; toda escritura va por la API
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .anyRequest().authenticated())
                .webAuthn(webAuthn -> webAuthn
                        .rpId(properties.rpId())
                        .rpName(properties.rpName())
                        .allowedOrigins(properties.allowedOrigins().toArray(String[]::new)));
        return http.build();
    }
}
