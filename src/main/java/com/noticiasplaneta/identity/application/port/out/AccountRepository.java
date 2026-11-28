package com.noticiasplaneta.identity.application.port.out;

import com.noticiasplaneta.identity.domain.Account;

import java.util.List;
import java.util.Optional;

public interface AccountRepository {

    Optional<Account> findByEmail(String email);

    /** Todas las cuentas del backoffice, para la gestión de accesos. */
    List<Account> findAll();

    Account save(Account account);

    boolean exists();
}
