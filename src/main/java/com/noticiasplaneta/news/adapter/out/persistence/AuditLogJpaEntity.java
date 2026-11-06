package com.noticiasplaneta.news.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "audit_log")
class AuditLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "actor_id", nullable = false)
    String actorId;

    @Column(name = "actor_type", nullable = false)
    String actorType;

    @Column(nullable = false)
    String action;

    @Column(nullable = false)
    String entity;

    @Column(name = "entity_id")
    String entityId;

    @Column(name = "before_state")
    String before;

    @Column(name = "after_state")
    String after;

    @Column(nullable = false)
    Instant at;

    protected AuditLogJpaEntity() {
    }
}
