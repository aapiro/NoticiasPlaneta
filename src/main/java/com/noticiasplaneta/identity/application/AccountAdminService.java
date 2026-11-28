package com.noticiasplaneta.identity.application;

import com.noticiasplaneta.identity.application.port.in.AccountAdminUseCase;
import com.noticiasplaneta.identity.application.port.out.AccountRepository;
import com.noticiasplaneta.identity.domain.Account;
import com.noticiasplaneta.identity.domain.AccountState;
import com.noticiasplaneta.identity.domain.Role;
import com.noticiasplaneta.shared.domain.AuditEntry;
import com.noticiasplaneta.shared.domain.AuditPort;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class AccountAdminService implements AccountAdminUseCase {

    private final AccountRepository accounts;
    private final AuditPort audit;
    private final Clock clock;

    public AccountAdminService(AccountRepository accounts, AuditPort audit, Clock clock) {
        this.accounts = accounts;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public List<Cuenta> cuentas(String solicitanteEmail) {
        var yo = accounts.findByEmail(solicitanteEmail).orElse(null);
        return accounts.findAll().stream()
                .map(a -> new Cuenta(a.id(), a.email(), a.displayName(), a.role(), a.state(),
                        a.role() == Role.ADMIN,
                        yo != null && a.id().equals(yo.id())))
                .toList();
    }

    @Override
    public Resultado cambiarEstado(String solicitanteEmail, UUID objetivoId, boolean activar) {
        var solicitante = accounts.findByEmail(solicitanteEmail).orElse(null);

        // Defensa en profundidad: la URL ya está detrás de hasRole('ADMIN'), pero si
        // mañana se añade otro camino a este servicio, esto sigue cerrado.
        if (solicitante == null || solicitante.role() != Role.ADMIN) {
            return Resultado.SIN_PERMISO;
        }

        var objetivo = accounts.findAll().stream()
                .filter(a -> a.id().equals(objetivoId))
                .findFirst()
                .orElse(null);
        if (objetivo == null) {
            return Resultado.NO_EXISTE;
        }

        // La raíz no se apaga a sí misma: sin ella no hay quien vuelva a encender el panel.
        if (objetivo.role() == Role.ADMIN) {
            return Resultado.ES_LA_RAIZ;
        }

        var nuevo = new Account(objetivo.id(), objetivo.email(), objetivo.displayName(),
                objetivo.role(), activar ? AccountState.ACTIVE : AccountState.DISABLED,
                objetivo.passwordHash(), objetivo.totpSecret(), objetivo.sections());
        accounts.save(nuevo);

        audit.record(new AuditEntry(solicitante.email(), solicitante.role().name(),
                activar ? "ACCOUNT_ENABLED" : "ACCOUNT_DISABLED", "account",
                objetivo.id().toString(),
                objetivo.state().name(), activar ? "ACTIVE" : "DISABLED",
                Instant.now(clock)));
        return Resultado.HECHO;
    }
}
