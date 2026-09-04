package com.junsang.course_backend.domain.place.collection.pipeline.batch.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionStep;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTempStatus;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.pipeline.batch.ai.entity.OpenAiBatchTaggingJob;
import com.junsang.course_backend.domain.place.collection.pipeline.batch.ai.entity.OpenAiBatchTaggingStatus;
import com.junsang.course_backend.domain.place.collection.pipeline.batch.ai.repository.OpenAiBatchTaggingJobRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.common.ai.service.AiTaggingWriter;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import com.junsang.course_backend.domain.place.repository.TagRepository;
import com.junsang.course_backend.infra.openai.batch.OpenAiBatchClient;
import com.junsang.course_backend.infra.openai.config.OpenAiProperties;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class OpenAiBatchTaggingServiceTest {

    @Test
    void includesOpenAiRequestFailuresInTheNextBatchSubmission() {
        PlaceCollectionTempRepository tempRepository = mock(PlaceCollectionTempRepository.class);
        OpenAiBatchTaggingService service = new OpenAiBatchTaggingService(
                tempRepository,
                mock(TagRepository.class),
                mock(OpenAiBatchClient.class),
                new OpenAiProperties(URI.create("https://api.openai.com"), "test", "gpt-5.6-luna", null, null),
                new ObjectMapper(),
                mock(AiTaggingWriter.class),
                new OpenAiBatchJobWriter(tempRepository, mock(OpenAiBatchTaggingJobRepository.class))
        );
        when(tempRepository.findAiBatchSubmissionTargets(
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(List.of());

        Long jobId = service.submitPending(1_000);

        assertThat(jobId).isNull();
        verify(tempRepository).findAiBatchSubmissionTargets(
                eq(PlaceCollectionStep.AI_TAGGING),
                eq(PlaceCollectionTempStatus.PENDING),
                eq(PlaceCollectionTempStatus.FAILED),
                eq(PlaceRefinementErrorCode.OPENAI_API_REQUEST_FAILED),
                any(Pageable.class)
        );
    }

    @Test
    void requeuesTargetsWhenTheRemoteBatchFails() {
        PlaceCollectionTempRepository tempRepository = mock(PlaceCollectionTempRepository.class);
        OpenAiBatchTaggingJobRepository jobRepository = mock(OpenAiBatchTaggingJobRepository.class);
        OpenAiBatchClient batchClient = mock(OpenAiBatchClient.class);
        OpenAiBatchTaggingService service = new OpenAiBatchTaggingService(
                tempRepository,
                mock(TagRepository.class),
                batchClient,
                new OpenAiProperties(URI.create("https://api.openai.com"), "test", "gpt-5.6-luna", null, null),
                new ObjectMapper(),
                mock(AiTaggingWriter.class),
                new OpenAiBatchJobWriter(tempRepository, jobRepository)
        );
        OpenAiBatchTaggingJob job = OpenAiBatchTaggingJob.submitting(1);
        job.submit("batch-test");
        PlaceCollectionTemp temp = mock(PlaceCollectionTemp.class);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(batchClient.completedOutput("batch-test"))
                .thenThrow(new IllegalStateException("OpenAI Batch가 완료되지 않았습니다: failed"));
        when(tempRepository.findByAiBatchJobIdOrderById(1L)).thenReturn(List.of(temp));

        boolean collected = service.collect(1L);

        assertThat(collected).isFalse();
        assertThat(job.getStatus()).isEqualTo(OpenAiBatchTaggingStatus.FAILED);
        verify(temp).fail(
                eq(PlaceRefinementErrorCode.OPENAI_API_REQUEST_FAILED),
                eq("OpenAI Batch가 완료되지 않았습니다: failed")
        );
        verify(tempRepository).saveAll(List.of(temp));
    }
}
