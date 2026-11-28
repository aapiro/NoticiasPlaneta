package com.noticiasplaneta.identity.adapter.in.security;

import com.noticiasplaneta.identity.application.port.out.AccountRepository;
import com.noticiasplaneta.identity.domain.Account;
import com.noticiasplaneta.identity.domain.AccountState;
import com.noticiasplaneta.identity.domain.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.UUID;

/**
 * Crea —y mantiene— las dos cuentas de arranque del backoffice.
 *
 * <p><strong>ADMIN</strong> es la raíz: manda sobre todo y no se puede apagar a sí
 * misma. <strong>AGENT</strong> es la cuenta de trabajo de un agente de IA:
 * administra contenido, fuentes y métricas, pero no toca las cuentas, así que no
 * puede apagar a la raíz. La jerarquía va en un solo sentido.
 *
 * <p>Sin variables no hace nada y lo dice: nunca hay credenciales por defecto en
 * el código.
 */
@Configuration
class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    @Bean
    ApplicationRunner bootstrapAdmin(AccountRepository accounts,
                                     @Value("${admin.bootstrap.email:}") String email,
                                     @Value("${admin.bootstrap.password-hash:}") String passwordHash,
                                     @Value("${admin.bootstrap.totp-secret:}") String totpSecret,
                                     @Value("${admin.bootstrap.agent.email:}") String agentEmail,
                                     @Value("${admin.bootstrap.agent.password-hash:}") String agentHash,
                                     @Value("${admin.bootstrap.agent.totp-secret:}") String agentTotp) {
        return args -> {
            if (email.isBlank() || passwordHash.isBlank() || totpSecret.isBlank()) {
                if (!accounts.exists()) {
                    log.warn("Panel sin cuentas: define ADMIN_EMAIL, ADMIN_PASSWORD_HASH y ADMIN_TOTP_SECRET");
                }
            } else {
                asegurar(accounts, email, passwordHash, totpSecret, Role.ADMIN, "Administración");
            }

            if (!agentEmail.isBlank() && !agentHash.isBlank() && !agentTotp.isBlank()) {
                asegurar(accounts, agentEmail, agentHash, agentTotp, Role.AGENT, "Agente de IA");
            }
        };
    }

    /**
     * Crea la cuenta si no existe; si existe, le aplica el hash del entorno cuando
     * cambió. Sin eso, rotar una contraseña en Dokploy no serviría de nada y el
     * único camino sería un UPDATE por SQL en producción.
     */
    private static void asegurar(AccountRepository accounts, String email, String hash,
                                 String totp, Role rol, String nombre) {
        var existente = accounts.findByEmail(email);
        if (existente.isPresent()) {
            var cuenta = existente.get();
            if (!hash.equals(cuenta.passwordHash())) {
                accounts.save(new Account(cuenta.id(), cuenta.email(), cuenta.displayName(),
                        cuenta.role(), cuenta.state(), hash, cuenta.totpSecret(),
                        cuenta.sections()));
                log.info("Contraseña de {} actualizada desde el entorno", email);
            }
            return;
        }

        accounts.save(new Account(UUID.randomUUID(), email, nombre, rol,
                AccountState.ACTIVE, hash, totp, Set.of()));
        log.info("Cuenta {} creada para {}", rol, email);
    }
}
