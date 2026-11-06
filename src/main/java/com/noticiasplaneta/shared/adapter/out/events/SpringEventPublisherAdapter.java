package com.noticiasplaneta.shared.adapter.out.events;

import com.noticiasplaneta.shared.domain.EventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringEventPublisherAdapter implements EventPublisher {

    private final ApplicationEventPublisher delegate;

    SpringEventPublisherAdapter(ApplicationEventPublisher delegate) {
        this.delegate = delegate;
    }

    @Override
    public void publish(Object event) {
        delegate.publishEvent(event);
    }
}
