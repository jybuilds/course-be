package com.junsang.course_backend.domain.place.collection.pipeline.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.domain.place.collection.pipeline.support.error.*;
import com.junsang.course_backend.infra.openai.batch.OpenAiBatchClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiTaggingErrorHandler {
    private final OpenAiBatchJobWriter writer;
    public CollectionFailure classify(Throwable error) {
        if (error instanceof OpenAiBatchClient.SubmissionUnknownException)
            return new CollectionFailure(PlaceRefinementErrorCode.OPENAI_BATCH_SUBMISSION_UNKNOWN, "원격 접수 여부 확인 필요");
        if (error instanceof JsonProcessingException)
            return new CollectionFailure(PlaceRefinementErrorCode.AI_RESPONSE_PARSE_FAILED, "AI 결과 JSON 형식 오류");
        return CollectionFailureClassifier.classify("AI", error,
                error instanceof IllegalArgumentException ? PlaceRefinementErrorCode.AI_TAG_VALIDATION_FAILED
                        : PlaceRefinementErrorCode.UNEXPECTED_ERROR);
    }
    public CollectionFailure remote(int status, JsonNode error) {
        JsonNode detail = error != null && error.has("error") ? error.path("error") : error;
        String code = detail == null ? "" : detail.path("code").asText("") + " " + detail.path("type").asText("");
        if (code.contains("invalid_api_key") || code.contains("authentication")) status = 401;
        else if (code.contains("rate_limit") || code.contains("usage_limit") || code.contains("quota")) status = 429;
        else if (code.contains("invalid") || code.contains("content_filter")) status = 400;
        else if (code.contains("timeout")) status = 408;
        return CollectionFailureClassifier.http("AI", status, error, null);
    }
    public void submission(Long jobId, RuntimeException error) {
        CollectionFailure failure = error instanceof IllegalArgumentException
                ? new CollectionFailure(PlaceRefinementErrorCode.AI_API_INVALID_REQUEST, "서버 요청 구성 오류") : classify(error);
        writer.fail(jobId, failure);
    }
    public void item(Long jobId, String token, Long tempId, CollectionFailure failure) {
        writer.failItem(jobId, token, tempId, failure);
    }
}
