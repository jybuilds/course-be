package com.junsang.course_backend.domain.place.collection.pipeline.scheduler;

import com.junsang.course_backend.domain.place.collection.pipeline.kakao.service.KakaoAreaCollectionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "place-collection.scheduling.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class KakaoCollectionRecoveryScheduler {
    private final KakaoAreaCollectionService service;
    @Scheduled(fixedDelayString = "${place-collection.kakao.recovery-poll-millis:60000}")
    public void resume() {
        try { service.resumePendingJobs(100); }
        catch (RuntimeException error) { log.warn("카카오 복구 실행 실패: {}", error.getClass().getSimpleName()); }
    }
}
