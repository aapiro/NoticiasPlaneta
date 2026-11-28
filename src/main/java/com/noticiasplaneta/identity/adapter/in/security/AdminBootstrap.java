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
 * Crea la primera cuenta ADMIN desde variables de entorno.
 *
 * <p>Sin variables no hace nada y lo dice: el panel queda inaccesible hasta que se configure.
 * Nunca hay credenciales por defecto en el código.
 */
@Configuration
class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    @Bean
    ApplicationRunner bootstrapAdmin(AccountRepository accounts,
                                     @Value("${admin.bootstrap.email:}") String email,
                                     @Value("${admin.bootstrap.password-hash:}") String passwordHash,
                                     @Value("${admin.bootstrap.totp-secret:}") String totpSecret) {
        return args -> {
            if (email.isBlank() || passwordHash.isBlank() || totpSecret.isBlank()) {
                if (!accounts.exists()) {
                    log.warn("Panel sin cuentas: define ADMIN_EMAIL, ADMIN_PASSWORD_HASH y ADMIN_TOTP_SECRET");
                }
                return;
            }

            var existente = accounts.findByEmail(email);
            if (existente.isPresent()) {
                // Rotar la contraseña desde el entorno tiene que funcionar sin tocar la
                // base a mano. Si no, cambiar ADMIN_PASSWORD_HASH no sirve de nada y el
                // unico camino es un UPDATE por SQL en produccion.
                var cuenta = existente.get();
                if (!passwordHash.equals(cuenta.passwordHash())) {
                    accounts.save(new Account(cuenta.id(), cuenta.email(), cuenta.displayName(),
                            cuenta.role(), cuenta.state(), passwordHash, cuenta.totpSecret(),
                            cuenta.sections()));
                    log.info("Contraseña de {} actualizada desde el entorno", email);
                }
                return;
            }

            accounts.save(new Account(UUID.randomUUID(), email, "Administración", Role.ADMIN,
                    AccountState.ACTIVE, passwordHash, totpSecret, Set.of()));
            log.info("Cuenta ADMIN creada para {}", email);
        };
    }
}
