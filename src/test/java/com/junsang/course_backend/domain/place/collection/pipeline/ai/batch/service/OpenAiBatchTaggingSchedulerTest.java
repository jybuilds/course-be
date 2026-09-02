package com.junsang.course_backend.domain.place.collection.pipeline.ai.batch.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.junsang.course_backend.domain.place.collection.pipeline.ai.batch.entity.OpenAiBatchTaggingJob;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.batch.entity.OpenAiBatchTaggingStatus;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.batch.repository.OpenAiBatchTaggingJobRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiBatchTaggingSchedulerTest {

    @Test
    void collectsEverySubmittedBatch() {
        OpenAiBatchTaggingJobRepository jobRepository = mock(OpenAiBatchTaggingJobRepository.class);
        OpenAiBatchTaggingService service = mock(OpenAiBatchTaggingService.class);
        OpenAiBatchTaggingJob job = OpenAiBatchTaggingJob.submitting(1);
        when(jobRepository.findByStatus(OpenAiBatchTaggingStatus.SUBMITTED)).thenReturn(List.of(job));

        OpenAiBatchTaggingScheduler scheduler = new OpenAiBatchTaggingScheduler(jobRepository, service);
        scheduler.collectSubmittedBatches();

        verify(service).collect(job.getId());
    }
}
