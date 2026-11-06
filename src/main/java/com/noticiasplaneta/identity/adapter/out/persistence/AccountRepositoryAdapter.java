package com.noticiasplaneta.identity.adapter.out.persistence;

import com.noticiasplaneta.identity.application.port.out.AccountRepository;
import com.noticiasplaneta.identity.domain.Account;
import com.noticiasplaneta.identity.domain.AccountState;
import com.noticiasplaneta.identity.domain.Role;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashSet;
import java.util.Optional;

@Component
class AccountRepositoryAdapter implements AccountRepository {

    private final SpringDataAccountRepository jpa;
    private final Clock clock;

    AccountRepositoryAdapter(SpringDataAccountRepository jpa, Clock clock) {
        this.jpa = jpa;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Account> findByEmail(String email) {
        return jpa.findByEmail(email.toLowerCase()).map(this::toDomain);
    }

    @Override
    @Transactional
    public Account save(Account account) {
        AccountJpaEntity e = jpa.findById(account.id()).orElseGet(AccountJpaEntity::new);
        e.id = account.id();
        e.email = account.email().toLowerCase();
        e.displayName = account.displayName();
        e.role = account.role().name();
        e.state = account.state().name();
        e.passwordHash = account.passwordHash();
        e.totpSecret = account.totpSecret();
        e.createdAt = clock.instant();
        e.sections = new HashSet<>(account.sections());
        return toDomain(jpa.save(e));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists() {
        return jpa.count() > 0;
    }

    private Account toDomain(AccountJpaEntity e) {
        return new Account(e.id, e.email, e.displayName, Role.valueOf(e.role),
                AccountState.valueOf(e.state), e.passwordHash, e.totpSecret, e.sections);
    }
}
