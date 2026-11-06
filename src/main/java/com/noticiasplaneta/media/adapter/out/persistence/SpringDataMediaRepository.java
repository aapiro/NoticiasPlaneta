package com.noticiasplaneta.media.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataMediaRepository extends JpaRepository<MediaAssetJpaEntity, UUID> {

    Optional<MediaAssetJpaEntity> findBySha256(String sha256);
}
