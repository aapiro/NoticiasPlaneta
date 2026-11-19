package com.noticiasplaneta.ai.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataFeatureFlagRepository extends JpaRepository<FeatureFlagJpaEntity, String> {
}
