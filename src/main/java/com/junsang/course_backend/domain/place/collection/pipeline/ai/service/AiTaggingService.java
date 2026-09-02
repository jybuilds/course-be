package com.junsang.course_backend.domain.place.collection.pipeline.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.dto.AiTaggingBatchResponse;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.dto.AiTaggingResponse;
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
import com.junsang.course_backend.global.exception.BusinessException;
import com.junsang.course_backend.infra.openai.OpenAiClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/// 네이버 정제가 끝난 Temp를 OpenAI로 분류하고 최종 장소 저장을 조율한다.
@Service
@RequiredArgsConstructor
public class AiTaggingService {

    private static final int MAX_BATCH_SIZE = 20;
    private static final String INSTRUCTIONS = """
            한국 장소의 관련 태그와 적합도를 평가한다.
            입력 placeType은 수집 단계에서 확정된 값이므로 분류하거나 변경하지 않는다.
            제공된 활성 태그 코드만 사용하고, 장소명과 카카오·네이버 카테고리를 우선 근거로 태그를 선택한다.
            블로그 보조 자료는 검색 결과라 잡음이 있을 수 있으므로, 장소명·카테고리 판단을 보강할 때만 사용한다.
            조용한, 로맨틱한 같은 분위기 태그는 입력 정보만으로 확실하지 않으면 선택하지 않는다.
            각 장소에 근거가 있는 태그만 %d개 이상 %d개 이하로 반환한다.
            가중치는 0부터 %d 사이의 정수다. 점수가 높을수록 장소를 더 잘 대표하는 태그다.
            """.formatted(
            AiTaggingPolicy.MIN_TAG_COUNT,
            AiTaggingPolicy.MAX_TAG_COUNT,
            AiTaggingPolicy.MAX_TAG_WEIGHT
    );

    private final PlaceCollectionTempRepository tempRepository;
    private final TagRepository tagRepository;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;
    private final AiTaggingWriter writer;

    // Temp 한 건을 AI로 태깅하고 최종 Place로 저장한다.
    public AiTaggingResponse tag(Long tempId) {
        // 단건 실행은 운영 중 실패 건 재시도와 수동 검증에 사용한다.
        PlaceCollectionTemp temp = findTemp(tempId);
        if (!canTag(temp)) {
            return AiTaggingResponse.invalidStep(temp);
        }
        return process(List.of(temp)).getFirst();
    }

    // AI 태깅 대기 데이터를 최대 20개까지 한 번의 OpenAI 요청으로 처리한다.
    public AiTaggingBatchResponse tagPending(int limit) {
        if (limit < 1 || limit > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("AI 태깅 개수는 1 이상 20 이하여야 합니다.");
        }

        // ID 순서로 대기열을 가져와 실행 순서를 예측 가능하게 유지한다.
        List<PlaceCollectionTemp> targets = tempRepository.findByProcessingStepAndStatusOrderByIdAsc(
                PlaceCollectionStep.AI_TAGGING,
                PlaceCollectionTempStatus.PENDING,
                PageRequest.of(0, limit)
        );
        List<AiTaggingResponse> results = process(targets);
        int successCount = (int) results.stream()
                .filter(AiTaggingResponse::success)
                .count();
        return new AiTaggingBatchResponse(
                limit,
                targets.size(),
                successCount,
                targets.size() - successCount,
                results
        );
    }

    // 대상을 처리 중으로 전환하고 OpenAI 결과를 Temp ID별로 저장한다.
    private List<AiTaggingResponse> process(List<PlaceCollectionTemp> targets) {
        if (targets.isEmpty()) {
            return List.of();
        }

        // AI가 사용할 수 있는 태그는 현재 활성 상태인 태그로 제한한다.
        List<Tag> activeTags = tagRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        if (activeTags.isEmpty()) {
            return failAll(targets, PlaceRefinementErrorCode.AI_RESPONSE_INVALID, "활성 태그가 없습니다.");
        }

        // 외부 요청 전에 처리 중 상태를 남겨 중복 실행을 막는다.
        targets.forEach(PlaceCollectionTemp::startAiTagging);
        tempRepository.saveAll(targets);
        try {
            // 여러 Place를 한 입력에 묶어 반복 프롬프트와 요청 수를 줄인다.
            String output = openAiClient.createStructuredResponse(
                    INSTRUCTIONS,
                    createInput(targets, activeTags),
                    createSchema(targets.size(), activeTags)
            );
            // 구조화 응답을 역직렬화하고 요청한 Temp ID와 정확히 대응되는지 확인한다.
            AiTaggingResultBatch batch = objectMapper.readValue(output, AiTaggingResultBatch.class);
            Map<Long, AiTaggingResult> resultsByTempId = validateResults(batch, targets);

            // 태그 코드 조회는 한 번만 수행해 이후 Place별 저장에서 재조회하지 않는다.
            Map<String, Tag> tagsByCode = activeTags.stream()
                    .collect(Collectors.toMap(Tag::getCode, Function.identity()));

            return targets.stream()
                    .map(temp -> complete(temp, resultsByTempId.get(temp.getId()), tagsByCode))
                    .toList();
        } catch (BusinessException exception) {
            return failAll(
                    targets,
                    PlaceRefinementErrorCode.OPENAI_API_REQUEST_FAILED,
                    exception.getMessage()
            );
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return failAll(
                    targets,
                    PlaceRefinementErrorCode.AI_RESPONSE_INVALID,
                    messageOf(exception)
            );
        }
    }

    // 장소별 검증·저장 실패가 다른 장소의 완료를 막지 않게 개별 처리한다.
    private AiTaggingResponse complete(
            PlaceCollectionTemp temp,
            AiTaggingResult result,
            Map<String, Tag> tagsByCode
    ) {
        try {
            return writer.complete(temp.getId(), result, tagsByCode);
        } catch (IllegalArgumentException exception) {
            return writer.fail(
                    temp.getId(),
                    PlaceRefinementErrorCode.AI_RESPONSE_INVALID,
                    messageOf(exception)
            );
        }
    }

    // OpenAI 요청에 필요한 활성 태그와 장소 정보를 JSON으로 만든다.
    private String createInput(
            List<PlaceCollectionTemp> targets,
            List<Tag> activeTags
    ) throws JsonProcessingException {
        // DB 엔티티 전체를 넘기지 않고 AI 판단에 필요한 값만 전달한다.
        TaggingInput input = new TaggingInput(
                activeTags.stream()
                        .map(tag -> new TagInput(tag.getCode(), tag.getDisplayName()))
                        .toList(),
                targets.stream()
                        .map(temp -> new PlaceInput(
                                temp.getId(),
                                temp.getName(),
                                temp.getDefaultPlaceType().name(),
                                temp.getKakaoCategoryName(),
                                temp.getNaverCategoryName(),
                                temp.getNaverBlogEvidence()
                        ))
                        .toList()
        );
        return objectMapper.writeValueAsString(input);
    }

    // 현재 활성 Tag 코드만 허용하는 JSON Schema를 만든다.
    private Map<String, Object> createSchema(int targetCount, List<Tag> activeTags) {
        // 태그 코드는 활성 Tag 코드만 허용해 임의 코드 생성을 차단한다.
        List<String> tagCodes = activeTags.stream()
                .map(Tag::getCode)
                .toList();
        Map<String, Object> tag = Map.of(
                "type", "object",
                "properties", Map.of(
                        "code", Map.of("type", "string", "enum", tagCodes),
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
                        "tags", Map.of(
                                "type", "array",
                                "items", tag,
                                "minItems", AiTaggingPolicy.MIN_TAG_COUNT,
                                "maxItems", AiTaggingPolicy.MAX_TAG_COUNT
                        )
                ),
                "required", List.of("tempId", "tags"),
                "additionalProperties", false
        );
        // 결과 수를 요청 Place 수와 같게 강제해 누락·추가 응답을 조기에 잡는다.
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

    // 응답에 모든 요청 Temp ID가 한 번씩 들어 있는지 검증한다.
    private Map<Long, AiTaggingResult> validateResults(
            AiTaggingResultBatch batch,
            List<PlaceCollectionTemp> targets
    ) {
        if (batch == null || batch.results() == null) {
            throw new IllegalArgumentException("AI 결과 목록이 비어 있습니다.");
        }

        // 중복 Temp ID가 있으면 어떤 Place 결과인지 보장할 수 없으므로 전체 응답을 거부한다.
        Map<Long, AiTaggingResult> resultsByTempId = new HashMap<>();
        for (AiTaggingResult result : batch.results()) {
            if (result == null
                    || result.tempId() == null
                    || resultsByTempId.put(result.tempId(), result) != null) {
                throw new IllegalArgumentException("AI 결과에 비어 있거나 중복된 Temp ID가 있습니다.");
            }
        }
        // 모든 요청 대상이 정확히 한 번씩 응답됐는지 확인한다.
        boolean allMatched = targets.stream()
                .allMatch(temp -> resultsByTempId.containsKey(temp.getId()));
        if (!allMatched || resultsByTempId.size() != targets.size()) {
            throw new IllegalArgumentException("AI 결과의 Temp ID가 요청 대상과 일치하지 않습니다.");
        }
        return resultsByTempId;
    }

    // 같은 오류로 실패한 대상을 각각 기록한다.
    private List<AiTaggingResponse> failAll(
            List<PlaceCollectionTemp> targets,
            PlaceRefinementErrorCode errorCode,
            String errorMessage
    ) {
        return targets.stream()
                .map(temp -> writer.fail(temp.getId(), errorCode, errorMessage))
                .toList();
    }

    // AI 태깅을 시작할 수 있는 단계와 상태인지 확인한다.
    private boolean canTag(PlaceCollectionTemp temp) {
        return temp.getProcessingStep() == PlaceCollectionStep.AI_TAGGING
                && (temp.getStatus() == PlaceCollectionTempStatus.PENDING
                || temp.getStatus() == PlaceCollectionTempStatus.FAILED);
    }

    // Temp를 조회하고 존재하지 않으면 입력 오류로 처리한다.
    private PlaceCollectionTemp findTemp(Long tempId) {
        return tempRepository.findById(tempId)
                .orElseThrow(() -> new IllegalArgumentException("AI 태깅할 임시 장소를 찾을 수 없습니다: " + tempId));
    }

    // 비어 있는 예외 메시지 대신 예외 이름을 기록한다.
    private String messageOf(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private record TaggingInput(
            List<TagInput> availableTags,
            List<PlaceInput> places
    ) {
    }

    private record TagInput(String code, String displayName) {
    }

    private record PlaceInput(
            Long tempId,
            String name,
            String defaultPlaceType,
            String kakaoCategory,
            String naverCategory,
            String blogEvidence
    ) {
    }
}
