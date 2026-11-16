package com.junsang.course_backend.domain.place.collection.pipeline.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.service.AiTaggingInputBuilder;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.PlaceCategoryRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.entity.OpenAiBatchTaggingJob;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.dto.AiTaggingResultBatch;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.dto.AiTaggingResultBatch.AiTaggingResult;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.service.AiTaggingPolicy;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.domain.place.entity.Tag;
import com.junsang.course_backend.domain.place.repository.TagRepository;
import com.junsang.course_backend.infra.openai.batch.OpenAiBatchClient;
import com.junsang.course_backend.infra.openai.config.AiProviderProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.junsang.course_backend.domain.place.collection.pipeline.support.error.*;
import org.springframework.stereotype.Service;

/// 초기·정기 수집 장소를 OpenAI Batch API로 비동기 태깅한다.
@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAiBatchTaggingService {

    private static final int MAX_SUBMIT_SIZE = 1_000;
    private static final int PLACES_PER_REQUEST = 10;

    private final TagRepository tagRepository;
    private final OpenAiBatchClient openAiBatchClient;
    private final AiProviderProperties openAiProperties;
    private final ObjectMapper objectMapper;
    private final OpenAiBatchJobWriter batchJobWriter;
    private final AiTaggingInputBuilder inputBuilder;
    private final AiBatchResultWriter resultWriter;
    private final AiTaggingErrorHandler errorHandler;

    // AI 태깅 대기 Place를 OpenAI Batch API에 제출하고 로컬 Job ID를 반환한다.
    public Long submitRetries() {
       return submit(batchJobWriter.claimRetries(MAX_SUBMIT_SIZE));
    }

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

        String batchId = null;
        try {
            List<Tag> tags = tagRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
            if (tags.isEmpty()) {
                batchJobWriter.fail(claim.jobId(), PlaceRefinementErrorCode.AI_NO_ACTIVE_TAGS, "활성 태그 설정 필요");
                return claim.jobId();
            }
            List<PlaceCategoryRule> rules = inputBuilder.findActiveRules();
            var job = batchJobWriter.find(claim.jobId());
            batchId = openAiBatchClient.submit(createJsonLines(claim.targets(), tags, rules), job.getProvider());
            batchJobWriter.markSubmitted(claim.jobId(), batchId);
            return claim.jobId();
        } catch (RuntimeException error) {
            // 접수 이후 DB 기록 실패는 새 배치로 재제출하면 중복될 수 있다.
            errorHandler.submission(claim.jobId(), batchId == null ? error : new OpenAiBatchClient.SubmissionUnknownException(error));
            throw error;
        }
    }

    public boolean collect(Long jobId) {
        String token = batchJobWriter.acquireCollection(jobId);
        if (token == null) return false;
        try {
            OpenAiBatchTaggingJob job = batchJobWriter.find(jobId);
            if (job.getProvider() == null || job.getOpenAiBatchId() == null) {
                batchJobWriter.pausePoll(jobId, token, new CollectionFailure(PlaceRefinementErrorCode.AI_BATCH_PROVIDER_UNRESOLVED,
                        "기존 배치의 공급자/원격 ID 확인 후 연결 필요"));
                return false;
            }
            var poll = openAiBatchClient.poll(job.getOpenAiBatchId(), job.getProvider());
            batchJobWriter.recordPoll(jobId, token, poll.status());
            if (!poll.terminal()) {
                if (job.getCreatedAt() != null && job.getCreatedAt().isBefore(java.time.LocalDateTime.now().minusHours(25)))
                    log.warn("AI 원격 배치 장기 실행: jobId={}, status={}", jobId, poll.status());
                return false;
            }
            Map<String, Tag> tags = tagRepository.findByIsActiveTrueOrderByDisplayOrderAsc().stream()
                    .collect(Collectors.toMap(Tag::getCode, Function.identity()));
            List<PlaceCategoryRule> rules = inputBuilder.findActiveRules();
            List<OpenAiBatchJobWriter.Item> items = batchJobWriter.items(jobId);
            if (items.isEmpty()) {
                batchJobWriter.pausePoll(jobId, token, new CollectionFailure(PlaceRefinementErrorCode.AI_BATCH_PROVIDER_UNRESOLVED,
                        "기존 배치의 제출 대상 기록 확인 필요"));
                return false;
            }
            Map<String, List<OpenAiBatchJobWriter.Item>> requests = items.stream()
                    .collect(Collectors.groupingBy(OpenAiBatchJobWriter.Item::requestKey));
            boolean invalidLine = false;
            for (String line : poll.output().lines().filter(value -> !value.isBlank()).toList()) {
                batchJobWriter.renewCollection(jobId, token);
                invalidLine |= !applyLine(jobId, token, line, requests, tags, rules);
            }
            CollectionFailure missing = switch (poll.status()) {
                case "expired" -> new CollectionFailure(PlaceRefinementErrorCode.AI_BATCH_EXPIRED, "원격 Batch 만료: 미완료 장소");
                case "cancelled" -> new CollectionFailure(PlaceRefinementErrorCode.AI_BATCH_CANCELLED, "원격 Batch 취소: 미완료 장소");
                case "failed" -> remoteBatchFailure(poll.errors());
                default -> new CollectionFailure(PlaceRefinementErrorCode.AI_RESPONSE_MISSING, "원격 결과에서 장소 누락");
            };
            batchJobWriter.failPending(jobId, token, missing);
            batchJobWriter.settle(jobId, token);
            if (invalidLine) batchJobWriter.fail(jobId, PlaceRefinementErrorCode.AI_BATCH_OUTPUT_INVALID, "요청 식별 불가 결과 포함");
            return !invalidLine;
        } catch (RuntimeException error) {
            CollectionFailure failure = errorHandler.classify(error);
            if (CollectionErrorPolicy.POLL_BLOCKED.contains(failure.code())) {
                batchJobWriter.pausePoll(jobId, token, failure);
                return false;
            }
            throw error;
        } finally {
            batchJobWriter.releaseCollection(jobId, token);
        }
    }

    private CollectionFailure remoteBatchFailure(JsonNode errors) {
        JsonNode error = errors != null && errors.path("data").isArray() && !errors.path("data").isEmpty()
                ? errors.path("data").get(0) : errors;
        if (error != null && !error.isNull() && !error.isMissingNode()) {
            CollectionFailure failure = errorHandler.remote(400, error);
            if (error.has("code") || failure.code() == PlaceRefinementErrorCode.AI_API_QUOTA_EXHAUSTED) return failure;
        }
        return new CollectionFailure(PlaceRefinementErrorCode.OPENAI_BATCH_REMOTE_FAILED, "원격 Batch 실패: 공급자 오류 확인 필요");
    }

    private boolean applyLine(Long jobId, String token, String line,
            Map<String, List<OpenAiBatchJobWriter.Item>> requests, Map<String, Tag> tags, List<PlaceCategoryRule> rules) {
        List<OpenAiBatchJobWriter.Item> items = null;
        try {
            JsonNode envelope = objectMapper.readTree(line);
            if (envelope == null || !envelope.isObject()) return false;
            items = requests.get(envelope.path("custom_id").asText());
            if (items == null) return false;
            JsonNode response = envelope.path("response");
            JsonNode body = response.path("body");
            if (!envelope.path("error").isNull() && !envelope.path("error").isMissingNode()) {
                failRequest(jobId, token, items, errorHandler.remote(500, envelope.path("error")));
                return true;
            }
            int status = response.path("status_code").asInt(0);
            if (status >= 400) {
                failRequest(jobId, token, items, errorHandler.remote(status, body));
                return true;
            }
            if (status < 200 || status >= 300) throw new IllegalArgumentException("HTTP 결과 상태 누락");
            if ("failed".equals(body.path("status").asText()) && body.hasNonNull("error")) {
                failRequest(jobId, token, items, errorHandler.remote(500, body));
                return true;
            }
            if ("incomplete".equals(body.path("status").asText())) {
                failRequest(jobId, token, items, new CollectionFailure(PlaceRefinementErrorCode.AI_RESPONSE_INCOMPLETE, "AI 출력 미완료"));
                return true;
            }
            String text = body.path("output_text").asText("");
            for (JsonNode output : body.path("output")) {
                for (JsonNode content : output.path("content")) {
                    if ("refusal".equals(content.path("type").asText())) {
                        failRequest(jobId, token, items, new CollectionFailure(PlaceRefinementErrorCode.AI_RESPONSE_REFUSED, "AI 응답 거절"));
                        return true;
                    }
                    if (text.isBlank() && "output_text".equals(content.path("type").asText())) text = content.path("text").asText("");
                }
            }
            if (text.isBlank()) throw new IllegalArgumentException("AI 결과 텍스트 없음");
            JsonNode payload = objectMapper.readTree(text);
            if (payload == null || !payload.isObject()) throw new IllegalArgumentException("AI 결과 객체 없음");
            JsonNode results = payload.path("results");
            if (!results.isArray()) throw new IllegalArgumentException("장소 결과 배열 누락");
            Set<Long> expected = items.stream().map(OpenAiBatchJobWriter.Item::tempId).collect(Collectors.toSet());
            Map<Long, Long> counts = new java.util.HashMap<>();
            for (JsonNode result : results) if (result.path("tempId").isIntegralNumber())
                counts.merge(result.path("tempId").longValue(), 1L, Long::sum);
            boolean valid = true;
            for (JsonNode result : results) {
                if (!result.path("tempId").isIntegralNumber() || !expected.contains(result.path("tempId").longValue())) {
                    valid = false;
                    continue;
                }
                Long id = result.path("tempId").longValue();
                if (counts.get(id) > 1) {
                    errorHandler.item(jobId, token, id, new CollectionFailure(PlaceRefinementErrorCode.AI_RESPONSE_DUPLICATE, "중복 장소 결과"));
                    continue;
                }
                batchJobWriter.renewCollection(jobId, token);
                try {
                    JsonNode scores = result.path("priorityScores");
                    if (!validScore(scores.path("experience")) || !validScore(scores.path("distinctiveness"))) {
                        errorHandler.item(jobId, token, id, new CollectionFailure(PlaceRefinementErrorCode.AI_SCORE_VALIDATION_FAILED, "점수 필드 누락/범위 오류"));
                        continue;
                    }
                    AiTaggingResult value = objectMapper.treeToValue(result, AiTaggingResult.class);
                    resultWriter.complete(jobId, token, value, tags, rules);
                } catch (Exception error) {
                    if (error instanceof OpenAiBatchJobWriter.CollectionLeaseLostException lost) throw lost;
                    // 앞 장소의 저장 실패가 같은 요청의 나머지 정상 결과를 막지 않는다.
                    errorHandler.item(jobId, token, id, errorHandler.classify(error));
                }
            }
            for (Long id : expected) if (!counts.containsKey(id)) errorHandler.item(jobId, token, id,
                    new CollectionFailure(PlaceRefinementErrorCode.AI_RESPONSE_MISSING, "요청한 장소 결과 누락"));
            return valid;
        } catch (Exception error) {
            if (error instanceof OpenAiBatchJobWriter.CollectionLeaseLostException lost) throw lost;
            if (items == null) return false;
            failRequest(jobId, token, items, error instanceof IllegalArgumentException
                    ? new CollectionFailure(PlaceRefinementErrorCode.AI_BATCH_OUTPUT_INVALID, "AI 요청 결과 구조 오류")
                    : errorHandler.classify(error));
            return true;
        }
    }

    private boolean validScore(JsonNode score) {
        return score.isIntegralNumber() && score.canConvertToInt() && score.intValue() >= -25 && score.intValue() <= 25;
    }

    private void failRequest(Long jobId, String token, List<OpenAiBatchJobWriter.Item> items, CollectionFailure failure) {
        for (var item : items) errorHandler.item(jobId, token, item.tempId(), failure);
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
                            "model", openAiProperties.selected().model(),
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
                        "keywords", Map.of("type", "array", "items", Map.of("type", "string")),
                        "priorityScores", AiTaggingPolicy.priorityScoresSchema(),
                        "tags", Map.of(
                                "type", "array",
                                "items", tag,
                                "minItems", AiTaggingPolicy.MIN_TAG_COUNT,
                                "maxItems", AiTaggingPolicy.MAX_TAG_COUNT
                        )
                ),
                "required", List.of("tempId", "placeType", "keywords", "priorityScores", "tags"),
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

}
