package com.noticiasplaneta.section.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "section_config")
class SectionConfigJpaEntity {

    @Id
    @Column(name = "slug", nullable = false)
    String slug;

    @Column(name = "nombre", nullable = false)
    String nombre;

    @Column(name = "descripcion")
    String descripcion;

    @Column(name = "activa", nullable = false)
    boolean activa;

    @Column(name = "orden", nullable = false)
    int orden;

    @Column(name = "max_piezas_por_edicion", nullable = false)
    int maxPiezas;

    @Column(name = "enfoque", nullable = false)
    String enfoque;

    @Column(name = "auto_publica", nullable = false)
    boolean autoPublica;

    @Column(name = "auto_limite_diario", nullable = false)
    int autoLimiteDiario;

    @Column(name = "actualizada_en", nullable = false)
    Instant actualizadaEn;

    protected SectionConfigJpaEntity() {
    }
}
