package com.junsang.course_backend.domain.place.collection.scheduling.config;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import com.junsang.course_backend.domain.place.collection.scheduling.policy.CollectionPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class CollectionSchedulingConfig {
    @Bean("collectionClock")
    public Clock collectionClock(CollectionSchedulingProperties properties) {
        return Clock.system(ZoneId.of(properties.getZone()));
    }

    @Primary
    @Bean("collectionPolicy")
    public CollectionPolicy collectionPolicy(List<CollectionPolicy> policies, CollectionSchedulingProperties properties) {
        return policies.stream().filter(p -> p.name().equalsIgnoreCase(properties.getPolicy()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("지원하지 않는 수집 정책: " + properties.getPolicy()));
    }

    @Bean
    public ThreadPoolTaskScheduler kakaoCollectionScheduler() {
        return scheduler("collection-kakao-");
    }

    @Bean
    public ThreadPoolTaskScheduler naverCollectionScheduler() {
        return scheduler("collection-naver-");
    }

    @Bean
    public ThreadPoolTaskScheduler aiSubmissionScheduler() {
        return scheduler("collection-ai-submit-");
    }

    @Bean
    public ThreadPoolTaskScheduler aiResultScheduler() {
        return scheduler("collection-ai-result-");
    }

    private ThreadPoolTaskScheduler scheduler(String prefix) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(prefix);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
