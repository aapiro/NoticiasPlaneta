package com.noticiasplaneta.section.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataSectionConfigRepository extends JpaRepository<SectionConfigJpaEntity, String> {

    List<SectionConfigJpaEntity> findAllByOrderByOrdenAsc();

    List<SectionConfigJpaEntity> findByActivaTrueOrderByOrdenAsc();
}
