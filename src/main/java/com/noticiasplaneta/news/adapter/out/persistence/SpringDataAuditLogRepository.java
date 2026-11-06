package com.noticiasplaneta.news.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAuditLogRepository extends JpaRepository<AuditLogJpaEntity, Long> {
}
