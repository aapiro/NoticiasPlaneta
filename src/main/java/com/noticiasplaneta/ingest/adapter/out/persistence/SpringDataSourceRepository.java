package com.noticiasplaneta.ingest.adapter.out.persistence;

import com.noticiasplaneta.ingest.domain.SourceState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataSourceRepository extends JpaRepository<SourceJpaEntity, UUID> {

    List<SourceJpaEntity> findByState(SourceState state);
}
