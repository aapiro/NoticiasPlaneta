package com.noticiasplaneta.identity.application.port.out;

import com.noticiasplaneta.identity.domain.Account;

import java.util.Optional;

public interface AccountRepository {

    Optional<Account> findByEmail(String email);

    Account save(Account account);

    boolean exists();
}
