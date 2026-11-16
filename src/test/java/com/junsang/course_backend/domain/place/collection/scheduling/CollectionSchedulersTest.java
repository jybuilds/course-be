package com.junsang.course_backend.domain.place.collection.scheduling;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.junsang.course_backend.domain.place.collection.scheduling.config.CollectionSchedulingProperties;
import com.junsang.course_backend.domain.place.collection.scheduling.policy.CollectionPolicy;
import com.junsang.course_backend.domain.place.collection.scheduling.service.*;
import com.junsang.course_backend.domain.place.collection.scheduling.scheduler.*;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.service.OpenAiBatchTaggingService;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.entity.*;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.repository.OpenAiBatchTaggingJobRepository;

class CollectionSchedulersTest {
    @Test void resultCollectionContinuesWhenNewWorkIsStoppedAndOneJobFails() {
        var policy = mock(CollectionPolicy.class);
        when(policy.settings()).thenReturn(new CollectionSchedulingProperties());
        var readiness = mock(CollectionReadiness.class);
        when(readiness.isReady()).thenReturn(true);
        var jobs = mock(OpenAiBatchTaggingJobRepository.class);
        var service = mock(OpenAiBatchTaggingService.class);
        var first = mock(OpenAiBatchTaggingJob.class);
        var second = mock(OpenAiBatchTaggingJob.class);
        when(first.getId()).thenReturn(1L);
        when(second.getId()).thenReturn(2L);
        when(jobs.findPollableJobs(any(), any(), any(), any())).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException()).when(service).collect(1L);
        new AiResultScheduler(policy, service, jobs, readiness).run();
        verify(service).collect(1L);
        verify(service).collect(2L);
        verify(policy, never()).canStartWork(any());
    }
    @Test void activeBatchLimitStopsSubmissionButDoesNotCollectOrFailJobs() {
        var policy = mock(CollectionPolicy.class);
        when(policy.settings()).thenReturn(new CollectionSchedulingProperties());
        var state = mock(CollectionExecutionState.class);
        when(state.canSubmitAi()).thenReturn(true);
        var jobs = mock(OpenAiBatchTaggingJobRepository.class);
        when(jobs.countByStatusIn(any())).thenReturn(2L);
        var service = mock(OpenAiBatchTaggingService.class);
        new AiSubmissionScheduler(policy, service, jobs, state).run();
        verifyNoInteractions(service);
    }
    @Test void notReadySkipsAllRemoteResultCalls() {
        var jobs = mock(OpenAiBatchTaggingJobRepository.class);
        var service = mock(OpenAiBatchTaggingService.class);
        new AiResultScheduler(mock(CollectionPolicy.class), service, jobs, new CollectionReadiness()).run();
        verifyNoInteractions(jobs, service);
    }
}
