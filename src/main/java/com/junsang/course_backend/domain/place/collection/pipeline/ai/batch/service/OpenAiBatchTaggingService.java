package com.junsang.course_backend.domain.place.collection.pipeline.ai.batch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.batch.entity.OpenAiBatchTaggingJob;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.batch.repository.OpenAiBatchTaggingJobRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.dto.AiTaggingResultBatch;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.dto.AiTaggingResultBatch.AiTaggingResult;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.service.AiTaggingPolicy;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.service.AiTaggingWriter;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionStep;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTempStatus;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import com.junsang.course_backend.domain.place.entity.Tag;
import com.junsang.course_backend.domain.place.repository.TagRepository;
import com.junsang.course_backend.infra.openai.batch.OpenAiBatchClient;
import com.junsang.course_backend.infra.openai.config.OpenAiProperties;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/// 초기·정기 수집 장소를 OpenAI Batch API로 비동기 태깅한다.
@Service
@RequiredArgsConstructor
public class OpenAiBatchTaggingService {

    private static final int MAX_SUBMIT_SIZE = 1_000;
    private static final int PLACES_PER_REQUEST = 10;
    private static final String INSTRUCTIONS = """
            한국 장소의 관련 태그와 적합도를 평가한다. 제공된 활성 태그 코드만 사용한다.
            placeType은 수집 단계에서 확정됐으므로 변경하지 않는다. 장소명과 카카오·네이버 카테고리를 우선 근거로 사용한다.
            블로그 자료는 보조 근거일 뿐이며, 확실하지 않은 분위기 태그는 선택하지 않는다.
            tags는 %d개 이상 %d개 이하이며 가중치는 0부터 %d 사이 정수다.
            """.formatted(
            AiTaggingPolicy.MIN_TAG_COUNT,
            AiTaggingPolicy.MAX_TAG_COUNT,
            AiTaggingPolicy.MAX_TAG_WEIGHT
    );

    private final PlaceCollectionTempRepository tempRepository;
    private final TagRepository tagRepository;
    private final OpenAiBatchTaggingJobRepository jobRepository;
    private final OpenAiBatchClient openAiBatchClient;
    private final OpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;
    private final AiTaggingWriter writer;

    // AI 태깅 대기 Place를 OpenAI Batch API에 제출하고 로컬 Job ID를 반환한다.
    public Long submitPending(int limit) {
        if (limit < 1 || limit > MAX_SUBMIT_SIZE) {
            throw new IllegalArgumentException("Batch 태깅 개수는 1 이상 1000 이하여야 합니다.");
        }
        List<PlaceCollectionTemp> targets = tempRepository.findAiBatchSubmissionTargets(
                PlaceCollectionStep.AI_TAGGING,
                PlaceCollectionTempStatus.PENDING,
                PlaceCollectionTempStatus.FAILED,
                PlaceRefinementErrorCode.OPENAI_API_REQUEST_FAILED,
                PageRequest.of(0, limit)
        );
        return submit(targets);
    }

    // 지정한 Area의 AI 태깅 대기 Place만 OpenAI Batch API에 제출한다.
    public Long submitPendingByArea(Long areaId, int limit) {
        if (limit < 1 || limit > MAX_SUBMIT_SIZE) {
            throw new IllegalArgumentException("Batch 태깅 개수는 1 이상 1000 이하여야 합니다.");
        }
        List<PlaceCollectionTemp> targets = tempRepository.findAiBatchSubmissionTargetsByAreaId(
                areaId,
                PlaceCollectionStep.AI_TAGGING,
                PlaceCollectionTempStatus.PENDING,
                PlaceCollectionTempStatus.FAILED,
                PlaceRefinementErrorCode.OPENAI_API_REQUEST_FAILED,
                PageRequest.of(0, limit)
        );
        return submit(targets);
    }

    // 선별된 AI 태깅 대기 Place를 하나의 OpenAI Batch Job으로 제출한다.
    private Long submit(List<PlaceCollectionTemp> targets) {
        if (targets.isEmpty()) {
            return null;
        }
        List<Tag> tags = tagRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        OpenAiBatchTaggingJob job = jobRepository.save(OpenAiBatchTaggingJob.submitting(targets.size()));

        // 제출 전 선점해 같은 Temp가 다른 Batch에 중복 포함되지 않게 한다.
        targets.forEach(temp -> temp.startAiBatchTagging(job.getId()));
        tempRepository.saveAll(targets);
        try {
            String batchId = openAiBatchClient.submit(createJsonLines(targets, tags));
            job.submit(batchId);
            jobRepository.save(job);
        } catch (RuntimeException exception) {
            // 외부 제출 실패로 PROCESSING 상태가 남지 않도록 즉시 재시도 가능한 실패로 되돌린다.
            targets.forEach(temp -> temp.fail(
                    PlaceRefinementErrorCode.OPENAI_API_REQUEST_FAILED,
                    messageOf(exception)
            ));
            tempRepository.saveAll(targets);
            job.fail(messageOf(exception));
            jobRepository.save(job);
            throw exception;
        }
        return job.getId();
    }

    // 완료된 OpenAI Batch 결과를 읽어 개별 Place와 PlaceTag에 반영한다.
    public boolean collect(Long jobId) {
        OpenAiBatchTaggingJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("AI Batch Job을 찾을 수 없습니다: " + jobId));
        if (job.getOpenAiBatchId() == null) {
            return false;
        }
        String output;
        try {
            output = openAiBatchClient.completedOutput(job.getOpenAiBatchId());
        } catch (IllegalStateException exception) {
            failBatchTargets(jobId, messageOf(exception));
            job.fail(messageOf(exception));
            jobRepository.save(job);
            return false;
        } catch (RuntimeException exception) {
            throw exception;
        }
        if (output == null) {
            return false;
        }
        Map<String, Tag> tags = tagRepository.findByIsActiveTrueOrderByDisplayOrderAsc().stream()
                .collect(Collectors.toMap(Tag::getCode, Function.identity()));
        Set<Long> targetIds = tempRepository.findByAiBatchJobIdOrderById(jobId).stream()
                .map(PlaceCollectionTemp::getId)
                .collect(Collectors.toCollection(HashSet::new));
        for (String line : output.lines().toList()) {
            applyLine(line, tags, targetIds);
        }

        // 응답 오류 등으로 저장되지 않은 대상만 실패 처리해 다음 Batch에서 재시도할 수 있게 한다.
        tempRepository.findByAiBatchJobIdOrderById(jobId).forEach(temp -> writer.fail(
                temp.getId(),
                PlaceRefinementErrorCode.AI_RESPONSE_INVALID,
                "OpenAI Batch 결과를 처리하지 못했습니다."
        ));
        job.complete();
        jobRepository.save(job);
        return true;
    }

    // 원격 Batch가 실패하면 묶여 있던 Temp를 다음 제출 대상인 OpenAI 요청 실패로 되돌린다.
    private void failBatchTargets(Long jobId, String errorMessage) {
        List<PlaceCollectionTemp> targets = tempRepository.findByAiBatchJobIdOrderById(jobId);
        targets.forEach(temp -> temp.fail(
                PlaceRefinementErrorCode.OPENAI_API_REQUEST_FAILED,
                errorMessage
        ));
        tempRepository.saveAll(targets);
    }

    // OpenAI Batch의 JSONL 한 줄에서 최대 10개 Place의 구조화 응답을 꺼내 최종 저장한다.
    private void applyLine(String line, Map<String, Tag> tags, Set<Long> targetIds) {
        try {
            JsonNode body = objectMapper.readTree(line).path("response").path("body");
            String text = body.path("output").get(0).path("content").get(0).path("text").asText();
            AiTaggingResultBatch batch = objectMapper.readValue(text, AiTaggingResultBatch.class);

            // 한 Responses 요청에 포함한 모든 Place 결과를 각각 저장한다.
            for (AiTaggingResult result : batch.results()) {
                if (result == null || result.tempId() == null || !targetIds.remove(result.tempId())) {
                    throw new IllegalArgumentException("OpenAI Batch 결과의 Temp ID가 요청 대상과 일치하지 않습니다.");
                }
                writer.complete(result.tempId(), result, tags);
            }
        } catch (Exception exception) {
            // 개별 응답 오류는 다른 Batch 결과 저장을 막지 않는다.
        }
    }

    // Place 10개씩 묶은 Responses 요청을 JSONL 한 줄로 만들어 반복 프롬프트 비용을 줄인다.
    private String createJsonLines(List<PlaceCollectionTemp> targets, List<Tag> tags) {
        List<String> lines = new ArrayList<>();
        for (int start = 0; start < targets.size(); start += PLACES_PER_REQUEST) {
            int end = Math.min(start + PLACES_PER_REQUEST, targets.size());
            lines.add(createJsonLine(targets.subList(start, end), tags));
        }
        return String.join("\n", lines);
    }

    // Batch 한 줄에 포함된 Place 목록과 동일한 결과 개수를 JSON Schema로 강제한다.
    private String createJsonLine(List<PlaceCollectionTemp> targets, List<Tag> tags) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "custom_id", "places-" + targets.getFirst().getId() + "-" + targets.getLast().getId(),
                    "method", "POST",
                    "url", "/v1/responses",
                    "body", Map.of(
                            "model", openAiProperties.model(),
                            "store", false,
                            "instructions", INSTRUCTIONS,
                            "input", objectMapper.writeValueAsString(Map.of(
                                    "availableTags", tags.stream().map(tag -> Map.of("code", tag.getCode(), "displayName", tag.getDisplayName())).toList(),
                                    "places", targets.stream()
                                            .map(temp -> Map.of(
                                                    "tempId", temp.getId(),
                                                    "name", temp.getName(),
                                                    "placeType", temp.getDefaultPlaceType().name(),
                                                    "kakaoCategory", value(temp.getKakaoCategoryName()),
                                                    "naverCategory", value(temp.getNaverCategoryName()),
                                                    "blogEvidence", value(temp.getNaverBlogEvidence())
                                            ))
                                            .toList()
                            )),
                            "reasoning", Map.of("effort", "none"),
                            "text", Map.of("format", Map.of("type", "json_schema", "name", "place_tagging", "strict", true, "schema", schema(tags, targets.size())))
                    )
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("OpenAI Batch 요청을 만들 수 없습니다.", exception);
        }
    }

    private Map<String, Object> schema(List<Tag> tags, int targetCount) {
        List<String> codes = tags.stream().map(Tag::getCode).toList();
        Map<String, Object> tag = Map.of("type", "object", "properties", Map.of("code", Map.of("type", "string", "enum", codes), "weight", Map.of("type", "integer", "minimum", 0, "maximum", AiTaggingPolicy.MAX_TAG_WEIGHT)), "required", List.of("code", "weight"), "additionalProperties", false);
        Map<String, Object> result = Map.of("type", "object", "properties", Map.of("tempId", Map.of("type", "integer"), "tags", Map.of("type", "array", "items", tag, "minItems", AiTaggingPolicy.MIN_TAG_COUNT, "maxItems", AiTaggingPolicy.MAX_TAG_COUNT)), "required", List.of("tempId", "tags"), "additionalProperties", false);
        return Map.of("type", "object", "properties", Map.of("results", Map.of("type", "array", "items", result, "minItems", targetCount, "maxItems", targetCount)), "required", List.of("results"), "additionalProperties", false);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String messageOf(RuntimeException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
