package com.noticiasplaneta.identity.adapter.out.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "account")
class AccountJpaEntity {

    @Id
    UUID id;

    @Column(nullable = false, unique = true)
    String email;

    @Column(name = "display_name", nullable = false)
    String displayName;

    @Column(nullable = false)
    String role;

    @Column(nullable = false)
    String state;

    @Column(name = "password_hash", nullable = false)
    String passwordHash;

    @Column(name = "totp_secret", nullable = false)
    String totpSecret;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "account_section", joinColumns = @JoinColumn(name = "account_id"))
    @Column(name = "section_id")
    Set<String> sections = new HashSet<>();

    protected AccountJpaEntity() {
    }
}
