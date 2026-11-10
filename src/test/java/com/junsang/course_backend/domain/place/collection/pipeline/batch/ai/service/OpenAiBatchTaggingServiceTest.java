package com.junsang.course_backend.domain.place.collection.pipeline.batch.ai.service;

import com.junsang.course_backend.domain.place.collection.pipeline.common.ai.service.AiTaggingInputBuilder;
import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.service.PlaceTypeClassifier;
import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.repository.PlaceCategoryRuleRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.junsang.course_backend.domain.place.collection.pipeline.common.ai.dto.AiTaggingResultBatch.AiTaggingResult;
import com.junsang.course_backend.domain.place.collection.pipeline.common.ai.dto.AiTaggingResultBatch.PriorityScores;
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
import com.junsang.course_backend.infra.openai.config.AiProviderProperties;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class OpenAiBatchTaggingServiceTest {

    @Test
    void selectsOnlyConfirmedTemporaryFailuresForRetry() {
        PlaceCollectionTempRepository tempRepository = mock(PlaceCollectionTempRepository.class);
        OpenAiBatchTaggingService service = new OpenAiBatchTaggingService(
                tempRepository,
                mock(TagRepository.class),
                mock(OpenAiBatchClient.class),
                openAiProperties(),
                new ObjectMapper(),
                mock(AiTaggingWriter.class),
                new OpenAiBatchJobWriter(tempRepository, mock(OpenAiBatchTaggingJobRepository.class)),
                new AiTaggingInputBuilder(new PlaceTypeClassifier(mock(PlaceCategoryRuleRepository.class)))
        );
        when(tempRepository.findAiBatchSubmissionTargets(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                any()
        )).thenReturn(List.of());

        Long jobId = service.submitPending(1_000);

        assertThat(jobId).isNull();
        verify(tempRepository).findAiBatchSubmissionTargets(
                eq(PlaceCollectionStep.AI_TAGGING),
                eq(PlaceCollectionTempStatus.PENDING),
                eq(PlaceCollectionTempStatus.FAILED),
                eq(List.of(PlaceRefinementErrorCode.OPENAI_BATCH_TEMPORARY_FAILURE)),
                any(java.time.LocalDateTime.class),
                any(java.time.LocalDateTime.class),
                eq(false),
                any(Pageable.class)
        );
    }

    @Test
    void submitsCompletedTargetsForRetagging() {
        PlaceCollectionTempRepository tempRepository = mock(PlaceCollectionTempRepository.class);
        OpenAiBatchTaggingService service = new OpenAiBatchTaggingService(
                tempRepository,
                mock(TagRepository.class),
                mock(OpenAiBatchClient.class),
                openAiProperties(),
                new ObjectMapper(),
                mock(AiTaggingWriter.class),
                new OpenAiBatchJobWriter(tempRepository, mock(OpenAiBatchTaggingJobRepository.class)),
                new AiTaggingInputBuilder(new PlaceTypeClassifier(mock(PlaceCategoryRuleRepository.class)))
        );
        when(tempRepository.findByProcessingStepAndStatusOrderByIdAsc(any(), any(), any()))
                .thenReturn(List.of());

        Long jobId = service.submitCompletedForRetagging(50);

        assertThat(jobId).isNull();
        verify(tempRepository).findByProcessingStepAndStatusOrderByIdAsc(
                eq(PlaceCollectionStep.AI_TAGGING),
                eq(PlaceCollectionTempStatus.COMPLETED),
                any(Pageable.class)
        );
    }

    @Test
    void collectsStructuredPriorityScoresFromBatchOutput() throws Exception {
        PlaceCollectionTempRepository tempRepository = mock(PlaceCollectionTempRepository.class);
        OpenAiBatchTaggingJobRepository jobRepository = mock(OpenAiBatchTaggingJobRepository.class);
        OpenAiBatchClient batchClient = mock(OpenAiBatchClient.class);
        AiTaggingWriter writer = mock(AiTaggingWriter.class);
        ObjectMapper mapper = new ObjectMapper();
        OpenAiBatchTaggingService service = new OpenAiBatchTaggingService(
                tempRepository,
                mock(TagRepository.class),
                batchClient,
                openAiProperties(),
                mapper,
                writer,
                new OpenAiBatchJobWriter(tempRepository, jobRepository),
                new AiTaggingInputBuilder(new PlaceTypeClassifier(mock(PlaceCategoryRuleRepository.class)))
        );
        OpenAiBatchTaggingJob job = OpenAiBatchTaggingJob.submitting(1);
        job.submit("batch-test");
        PlaceCollectionTemp temp = mock(PlaceCollectionTemp.class);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(temp.getId()).thenReturn(47L);
        when(tempRepository.findByAiBatchJobIdOrderById(1L)).thenReturn(List.of(temp));

        String result = """
                {"results":[{"tempId":47,"placeType":"MEAL","keywords":["브런치","샌드위치"],
                  "priorityScores":{"experience":4,"distinctiveness":-2},
                  "tags":[{"code":"BRUNCH","weight":90},{"code":"INDOOR","weight":80}]}]}
                """;
        var content = Map.of("type", "output_text", "text", result);
        var message = Map.of("type", "message", "content", List.of(content));
        var response = Map.of("status_code", 200, "body", Map.of("output", List.of(message)));
        when(batchClient.completedOutput("batch-test")).thenReturn(mapper.writeValueAsString(
                Map.of("custom_id", "places-47-47", "response", response)
        ));

        assertThat(service.collect(1L)).isTrue();
        var saved = ArgumentCaptor.forClass(AiTaggingResult.class);
        verify(writer).complete(eq(47L), saved.capture(), any(), any());
        assertThat(saved.getValue().keywords()).containsExactly("브런치", "샌드위치");
        assertThat(saved.getValue().priorityScores()).isEqualTo(new PriorityScores(4, -2));
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
                openAiProperties(),
                new ObjectMapper(),
                mock(AiTaggingWriter.class),
                new OpenAiBatchJobWriter(tempRepository, jobRepository),
                new AiTaggingInputBuilder(new PlaceTypeClassifier(mock(PlaceCategoryRuleRepository.class)))
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
        assertThat(job.getErrorCode()).isEqualTo(PlaceRefinementErrorCode.OPENAI_BATCH_REMOTE_FAILED);
        verify(temp).fail(
                eq(PlaceRefinementErrorCode.OPENAI_BATCH_REMOTE_FAILED),
                eq("OpenAI Batch가 완료되지 않았습니다: failed")
        );
        verify(tempRepository).saveAll(List.of(temp));
    }
    @Test
    void recordsProviderErrorWhenCompletedBatchContainsOnlyFailedRequests() {
        PlaceCollectionTempRepository tempRepository = mock(PlaceCollectionTempRepository.class);
        OpenAiBatchTaggingJobRepository jobRepository = mock(OpenAiBatchTaggingJobRepository.class);
        OpenAiBatchClient batchClient = mock(OpenAiBatchClient.class);
        OpenAiBatchTaggingService service = new OpenAiBatchTaggingService(
                tempRepository,
                mock(TagRepository.class),
                batchClient,
                openAiProperties(),
                new ObjectMapper(),
                mock(AiTaggingWriter.class),
                new OpenAiBatchJobWriter(tempRepository, jobRepository),
                new AiTaggingInputBuilder(new PlaceTypeClassifier(mock(PlaceCategoryRuleRepository.class)))
        );
        OpenAiBatchTaggingJob job = OpenAiBatchTaggingJob.submitting(1);
        job.submit("batch-test");
        PlaceCollectionTemp temp = mock(PlaceCollectionTemp.class);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(batchClient.completedOutput("batch-test"))
                .thenReturn("{\"response\":{\"status_code\":400,\"body\":{\"error\":{\"message\":\"Invalid prompt\"}}}}");
        when(tempRepository.findByAiBatchJobIdOrderById(1L)).thenReturn(List.of(temp));

        boolean collected = service.collect(1L);

        assertThat(collected).isFalse();
        assertThat(job.getStatus()).isEqualTo(OpenAiBatchTaggingStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo(PlaceRefinementErrorCode.OPENAI_BATCH_REQUEST_REJECTED);
        verify(temp).fail(
                eq(PlaceRefinementErrorCode.OPENAI_BATCH_REQUEST_REJECTED),
                eq("AI Batch HTTP 400: Invalid prompt")
        );
        verify(tempRepository).saveAll(List.of(temp));
    }
    private AiProviderProperties openAiProperties() {
        return new AiProviderProperties(
                "OPENAI",
                new AiProviderProperties.ProviderSettings(
                        URI.create("https://api.openai.com"),
                        "test",
                        "gpt-5.6-luna",
                        null,
                        null
                ),
                null
        );
    }

}
