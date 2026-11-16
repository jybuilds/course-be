package com.junsang.course_backend.domain.place.collection.scheduling.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CollectionReadiness {
    private volatile boolean ready;

    public boolean isReady() {
        return ready;
    }

    @EventListener
    public void markReady(ApplicationReadyEvent event) {
        ready = true;
    }
}
