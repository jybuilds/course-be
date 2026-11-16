package com.junsang.course_backend.domain.place.collection.scheduling;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.service.KakaoAreaCollectionService;
import com.junsang.course_backend.domain.place.collection.pipeline.naver.service.PlaceRefinementService;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.service.OpenAiBatchTaggingService;
import com.junsang.course_backend.domain.place.collection.scheduling.policy.CollectionPolicy;
import com.junsang.course_backend.domain.place.collection.scheduling.service.CollectionReadiness;

@SpringBootTest(properties = {"place-collection.scheduling.enabled=true", "place-collection.scheduling.start-at=23:59:59",
        "place-collection.recovery.single-instance=false"})
@EnabledIfEnvironmentVariable(named="DB_URL", matches=".*course_recovery_test.*")
class CollectionSchedulingContextTest {
    @Autowired ApplicationContext context;
    @Autowired CollectionPolicy policy;
    @Autowired CollectionReadiness readiness;
    @MockitoBean KakaoAreaCollectionService kakao;
    @MockitoBean PlaceRefinementService naver;
    @MockitoBean OpenAiBatchTaggingService ai;

    @Test void registersFourScheduledMethodsUsingTheSelectedPolicySettings() {
        assertThat(policy.name()).isEqualTo("INIT");
        assertThat(readiness.isReady()).isTrue();
        assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).hasSize(4);
        assertThat(context.containsBean("kakaoCollectionScheduler")).isTrue();
        assertThat(context.containsBean("naverCollectionScheduler")).isTrue();
        assertThat(context.containsBean("aiSubmissionScheduler")).isTrue();
        assertThat(context.containsBean("aiResultScheduler")).isTrue();
    }
}
