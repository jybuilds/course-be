package com.junsang.course_backend.domain.place.collection.pipeline.batch.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.junsang.course_backend.domain.place.collection.pipeline.common.ai.service.AiTaggingInputBuilder;
import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.entity.PlaceCategoryRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.junsang.course_backend.domain.place.collection.pipeline.batch.ai.entity.OpenAiBatchTaggingJob;
import com.junsang.course_backend.domain.place.collection.pipeline.common.ai.dto.AiTaggingResultBatch;
import com.junsang.course_backend.domain.place.collection.pipeline.common.ai.dto.AiTaggingResultBatch.AiTaggingResult;
import com.junsang.course_backend.domain.place.collection.pipeline.common.ai.service.AiTaggingPolicy;
import com.junsang.course_backend.domain.place.collection.pipeline.common.ai.service.AiTaggingWriter;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import com.junsang.course_backend.domain.place.entity.Tag;
import com.junsang.course_backend.domain.place.repository.TagRepository;
import com.junsang.course_backend.global.exception.BusinessException;
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
import org.springframework.stereotype.Service;

/// 초기·정기 수집 장소를 OpenAI Batch API로 비동기 태깅한다.
@Service
@RequiredArgsConstructor
public class OpenAiBatchTaggingService {

    private static final int MAX_SUBMIT_SIZE = 1_000;
    private static final int PLACES_PER_REQUEST = 10;

    private final PlaceCollectionTempRepository tempRepository;
    private final TagRepository tagRepository;
    private final OpenAiBatchClient openAiBatchClient;
    private final OpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;
    private final AiTaggingWriter writer;
    private final OpenAiBatchJobWriter batchJobWriter;
    private final AiTaggingInputBuilder inputBuilder;

    // AI 태깅 대기 Place를 OpenAI Batch API에 제출하고 로컬 Job ID를 반환한다.
    public Long submitPending(int limit) {
        validateLimit(limit);
        return submit(batchJobWriter.claimPending(limit));
    }

    // 완료된 Temp를 기존 네이버·블로그 근거로 다시 AI 태깅한다.
    public Long submitCompletedForRetagging(int limit) {
        validateLimit(limit);
        return submit(batchJobWriter.claimCompleted(limit));
    }

    // 지정한 Area의 AI 태깅 대기 Place만 OpenAI Batch API에 제출한다.
    public Long submitPendingByArea(Long areaId, int limit) {
        validateLimit(limit);
        return submit(batchJobWriter.claimPendingByArea(areaId, limit));
    }

    // 선별된 AI 태깅 대기 Place를 하나의 OpenAI Batch Job으로 제출한다.
    private Long submit(OpenAiBatchJobWriter.BatchClaim claim) {
        if (claim == null) {
            return null;
        }

        // 제출 시점의 활성 태그를 스키마에 고정해 비활성·임의 태그 응답을 차단한다.
        List<Tag> tags = tagRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        List<PlaceCategoryRule> rules = inputBuilder.findActiveRules();
        try {
            String batchId = openAiBatchClient.submit(createJsonLines(claim.targets(), tags, rules));
            batchJobWriter.markSubmitted(claim.jobId(), batchId);
        } catch (BusinessException exception) {
            // 키 누락·외부 API 실패는 Temp와 로컬 Batch Job에 같은 사유로 남긴다.
            batchJobWriter.fail(claim.jobId(), openAiErrorCode(exception), exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            // 외부 제출 실패로 PROCESSING 상태가 남지 않도록 즉시 재시도 가능한 실패로 되돌린다.
            batchJobWriter.fail(
                    claim.jobId(),
                    PlaceRefinementErrorCode.OPENAI_BATCH_SUBMISSION_FAILED,
                    messageOf(exception)
            );
            throw exception;
        }
        return claim.jobId();
    }

    // 완료된 OpenAI Batch 결과를 읽어 개별 Place와 PlaceTag에 반영한다.
    public boolean collect(Long jobId) {
        OpenAiBatchTaggingJob job = batchJobWriter.find(jobId);
        if (job.getOpenAiBatchId() == null) {
            return false;
        }
        String output;
        try {
            output = openAiBatchClient.completedOutput(job.getOpenAiBatchId());
        } catch (IllegalStateException exception) {
            batchJobWriter.fail(
                    jobId,
                    PlaceRefinementErrorCode.OPENAI_BATCH_REMOTE_FAILED,
                    messageOf(exception)
            );
            return false;
        } catch (RuntimeException exception) {
            throw exception;
        }
        if (output == null) {
            // OpenAI가 아직 처리 중이면 SUBMITTED 상태를 유지해 스케줄러가 다음 주기에 다시 조회한다.
            return false;
        }

        // 결과 적용 중 태그를 매번 재조회하지 않도록 현재 활성 태그를 한 번만 맵으로 만든다.
        Map<String, Tag> tags = tagRepository.findByIsActiveTrueOrderByDisplayOrderAsc().stream()
                .collect(Collectors.toMap(Tag::getCode, Function.identity()));
        List<PlaceCategoryRule> rules = inputBuilder.findActiveRules();
        Set<Long> targetIds = tempRepository.findByAiBatchJobIdOrderById(jobId).stream()
                .map(PlaceCollectionTemp::getId)
                .collect(Collectors.toCollection(HashSet::new));
        BatchOutputFailure failure = null;
        for (String line : output.lines().toList()) {
            // 한 JSONL 줄의 실패가 다른 줄의 정상 결과 반영을 막지 않게 끝까지 처리한다.
            BatchOutputFailure lineFailure = applyLine(line, tags, rules, targetIds);
            if (failure == null && lineFailure != null) {
                failure = lineFailure;
            }
        }

        if (failure != null) {
            // 한 줄이라도 구조·검증 오류가 있으면 아직 남은 Temp와 Batch Job을 같은 코드로 실패 처리한다.
            batchJobWriter.fail(jobId, failure.errorCode(), failure.message());
            return false;
        }
        if (!targetIds.isEmpty()) {
            // JSON Schema가 있어도 일부 결과가 누락될 수 있으므로 요청 ID 전체 소비 여부를 마지막에 확인한다.
            batchJobWriter.fail(
                    jobId,
                    PlaceRefinementErrorCode.AI_BATCH_OUTPUT_INVALID,
                    "OpenAI Batch 결과에 요청한 Temp 일부가 없습니다: " + targetIds
            );
            return false;
        }
        batchJobWriter.complete(jobId);
        return true;
    }

    // OpenAI Batch의 JSONL 한 줄에서 최대 10개 Place의 구조화 응답을 꺼내 최종 저장한다.
    private BatchOutputFailure applyLine(
            String line,
            Map<String, Tag> tags,
            List<PlaceCategoryRule> rules,
            Set<Long> targetIds
    ) {
        try {
            // Batch API는 JSONL 외피 안에 Responses API의 구조화 텍스트를 넣어 반환한다.
            JsonNode body = objectMapper.readTree(line).path("response").path("body");
            JsonNode output = body.path("output");
            if (!output.isArray() || output.isEmpty()) {
                throw new IllegalArgumentException("OpenAI Batch 응답에 output이 없습니다.");
            }
            JsonNode content = output.get(0).path("content");
            if (!content.isArray() || content.isEmpty()) {
                throw new IllegalArgumentException("OpenAI Batch 응답에 content가 없습니다.");
            }
            String text = content.get(0).path("text").asText();
            if (text.isBlank()) {
                throw new IllegalArgumentException("OpenAI Batch 응답 본문이 비어 있습니다.");
            }
            AiTaggingResultBatch batch = objectMapper.readValue(text, AiTaggingResultBatch.class);

            // 한 Responses 요청에 포함한 모든 Place 결과를 각각 저장한다.
            for (AiTaggingResult result : batch.results()) {
                if (result == null || result.tempId() == null || !targetIds.remove(result.tempId())) {
                    return new BatchOutputFailure(
                            PlaceRefinementErrorCode.AI_RESPONSE_TARGET_MISMATCH,
                            "OpenAI Batch 결과의 Temp ID가 요청 대상과 일치하지 않습니다."
                    );
                }
                writer.complete(result.tempId(), result, tags, rules);
            }
            return null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            // JSONL 외피 또는 구조화 응답 JSON을 읽지 못한 경우다.
            return new BatchOutputFailure(
                    PlaceRefinementErrorCode.AI_RESPONSE_PARSE_FAILED,
                    messageOf(exception)
            );
        } catch (IllegalArgumentException exception) {
            // output/content 누락처럼 응답 형식이 계약과 다른 경우다.
            return new BatchOutputFailure(
                    PlaceRefinementErrorCode.AI_BATCH_OUTPUT_INVALID,
                    messageOf(exception)
            );
        } catch (RuntimeException exception) {
            // Place·PlaceTag 저장 전 검증 실패는 태그 검증 오류로 남긴다.
            return new BatchOutputFailure(
                    PlaceRefinementErrorCode.AI_TAG_VALIDATION_FAILED,
                    messageOf(exception)
            );
        }
    }

    // Place 10개씩 묶은 Responses 요청을 JSONL 한 줄로 만들어 반복 프롬프트 비용을 줄인다.
    private String createJsonLines(
            List<PlaceCollectionTemp> targets,
            List<Tag> tags,
            List<PlaceCategoryRule> rules
    ) {
        // 규칙과 장소 근거를 제출당 한 번 구성한 뒤 10개씩 나눈다.
        Map<String, Object> input = inputBuilder.create(targets, tags, rules);
        List<?> places = (List<?>) input.get("places");
        List<String> lines = new ArrayList<>();
        for (int start = 0; start < targets.size(); start += PLACES_PER_REQUEST) {
            int end = Math.min(start + PLACES_PER_REQUEST, targets.size());
            lines.add(createJsonLine(targets.subList(start, end), tags, Map.of(
                    "availableTags", input.get("availableTags"),
                    "typeRules", input.get("typeRules"),
                    "places", places.subList(start, end)
            )));
        }
        return String.join("\n", lines);
    }

    // Batch 한 줄에 포함된 Place 목록과 동일한 결과 개수를 JSON Schema로 강제한다.
    private String createJsonLine(List<PlaceCollectionTemp> targets, List<Tag> tags, Map<String, Object> input) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "custom_id", "places-" + targets.getFirst().getId() + "-" + targets.getLast().getId(),
                    "method", "POST",
                    "url", "/v1/responses",
                    "body", Map.of(
                            "model", openAiProperties.model(),
                            "store", false,
                            "instructions", AiTaggingPolicy.instructions(),
                            "input", objectMapper.writeValueAsString(input),
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
        Map<String, Object> tag = Map.of(
                "type", "object",
                "properties", Map.of(
                        "code", Map.of("type", "string", "enum", codes),
                        "weight", Map.of(
                                "type", "integer",
                                "minimum", 0,
                                "maximum", AiTaggingPolicy.MAX_TAG_WEIGHT
                        )
                ),
                "required", List.of("code", "weight"),
                "additionalProperties", false
        );
        Map<String, Object> result = Map.of(
                "type", "object",
                "properties", Map.of(
                        "tempId", Map.of("type", "integer"),
                        "placeType", Map.of(
                                "type", "string",
                                "enum", List.of("ACTIVITY", "MEAL", "CAFE")
                        ),
                        "tags", Map.of(
                                "type", "array",
                                "items", tag,
                                "minItems", AiTaggingPolicy.MIN_TAG_COUNT,
                                "maxItems", AiTaggingPolicy.MAX_TAG_COUNT
                        )
                ),
                "required", List.of("tempId", "placeType", "tags"),
                "additionalProperties", false
        );
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "results", Map.of(
                                "type", "array",
                                "items", result,
                                "minItems", targetCount,
                                "maxItems", targetCount
                        )
                ),
                "required", List.of("results"),
                "additionalProperties", false
        );
    }


    // Batch API와 로컬 Job이 감당하는 제출 상한을 함께 지킨다.
    private void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_SUBMIT_SIZE) {
            throw new IllegalArgumentException("Batch 태깅 개수는 1 이상 1000 이하여야 합니다.");
        }
    }

    private String messageOf(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    // OpenAI 인프라 오류를 Temp와 Batch Job에 남길 세부 코드로 변환한다.
    private PlaceRefinementErrorCode openAiErrorCode(BusinessException exception) {
        return switch (exception.getErrorCode()) {
            case OPENAI_API_KEY_NOT_CONFIGURED -> PlaceRefinementErrorCode.OPENAI_API_KEY_NOT_CONFIGURED;
            case OPENAI_API_EMPTY_RESPONSE -> PlaceRefinementErrorCode.OPENAI_API_EMPTY_RESPONSE;
            default -> PlaceRefinementErrorCode.OPENAI_API_REQUEST_FAILED;
        };
    }

    private record BatchOutputFailure(
            PlaceRefinementErrorCode errorCode,
            String message
    ) {
    }
}
