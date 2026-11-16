package com.junsang.course_backend.domain.place.collection.pipeline.scheduler;

import com.junsang.course_backend.domain.place.collection.pipeline.naver.service.PlaceRefinementService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "place-collection.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class NaverRefinementRecoveryScheduler {
    private final PlaceRefinementService service;
    @Scheduled(fixedDelayString = "${place-collection.naver.recovery-poll-millis:60000}")
    public void resume() {
        try { service.refineRecoverable(100); }
        catch (RuntimeException error) { log.warn("네이버 복구 실행 실패: {}", error.getClass().getSimpleName()); }
    }
}
