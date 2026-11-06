package com.noticiasplaneta.identity.application;

import com.noticiasplaneta.identity.application.port.out.AccountRepository;
import com.noticiasplaneta.identity.domain.Account;
import com.noticiasplaneta.identity.domain.Totp;
import com.noticiasplaneta.identity.domain.TotpSecret;
import com.noticiasplaneta.shared.domain.DomainException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Autenticación del backoffice (ADR-010, provisional): contraseña + TOTP obligatorio.
 *
 * <p>Los errores son distintos por dentro (cuenta inexistente, contraseña mal, TOTP mal) pero
 * el mensaje hacia fuera es siempre el mismo: no se le dice a un atacante qué ha acertado.
 */
@Service
public class AdminAuthenticator {

    private static final String GENERIC = "Credenciales incorrectas";

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AdminAuthenticator(AccountRepository accounts, PasswordEncoder passwordEncoder, Clock clock) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public Account authenticate(String email, String password, String totp) {
        if (email == null || password == null || totp == null) {
            throw new DomainException("BAD_CREDENTIALS", GENERIC);
        }

        Account account = accounts.findByEmail(email.trim())
                .orElseThrow(() -> new DomainException("BAD_CREDENTIALS", GENERIC));

        if (!account.isActive()) {
            throw new DomainException("ACCOUNT_DISABLED", "La cuenta está desactivada");
        }

        if (!passwordEncoder.matches(password, account.passwordHash())) {
            throw new DomainException("BAD_CREDENTIALS", GENERIC);
        }

        if (account.requiresTotp() && !Totp.verify(new TotpSecret(account.totpSecret()), totp, clock.instant())) {
            throw new DomainException("BAD_TOTP", "Código de verificación incorrecto");
        }

        return account;
    }
}
