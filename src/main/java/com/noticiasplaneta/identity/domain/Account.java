package com.noticiasplaneta.identity.domain;

import com.noticiasplaneta.shared.domain.DomainException;

import java.util.Set;
import java.util.UUID;

/**
 * Cuenta del backoffice.
 *
 * <p>El hash de contraseña y el secreto TOTP no tienen getters públicos de escritura:
 * se rehidratan y se usan dentro del módulo de identidad.
 */
public record Account(UUID id, String email, String displayName, Role role, AccountState state,
                      String passwordHash, String totpSecret, Set<String> sections) {

    public Account {
        if (email == null || !email.contains("@")) {
            throw new DomainException("ACCOUNT_EMAIL_INVALID", "Email no válido: " + email);
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new DomainException("ACCOUNT_PASSWORD_REQUIRED", "La cuenta necesita hash de contraseña");
        }
        if (totpSecret == null || totpSecret.isBlank()) {
            throw new DomainException("ACCOUNT_TOTP_REQUIRED", "La cuenta necesita secreto TOTP");
        }
        sections = sections == null ? Set.of() : Set.copyOf(sections);
    }

    public boolean isActive() {
        return state == AccountState.ACTIVE;
    }

    public boolean coversSection(String sectionId) {
        return sections.contains(sectionId);
    }

    /** Roles que publican exigen segundo factor (F13). En fase 1 lo exigen todos. */
    public boolean requiresTotp() {
        return true;
    }
}
