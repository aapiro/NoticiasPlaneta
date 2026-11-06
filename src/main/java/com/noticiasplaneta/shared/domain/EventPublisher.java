package com.noticiasplaneta.shared.domain;

/** Puerto de salida para publicar eventos de dominio. */
public interface EventPublisher {
    void publish(Object event);
}
