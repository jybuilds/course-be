package com.junsang.course_backend.domain.place.collection.pipeline.support.error;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CollectionErrorPolicyTest {
    final LocalDateTime now = LocalDateTime.of(2026,11,13,12,0);
    @Test void retriesThreeTimesThenDaily() {
        var failure=new CollectionFailure(PlaceRefinementErrorCode.AI_TAG_VALIDATION_FAILED,"test");
        assertThat(CollectionErrorPolicy.nextRetry(failure,0,now)).isEqualTo(now.plusMinutes(5));
        assertThat(CollectionErrorPolicy.nextRetry(failure,2,now)).isEqualTo(now.plusMinutes(5));
        assertThat(CollectionErrorPolicy.nextRetry(failure,3,now)).isEqualTo(now.plusHours(24));
        assertThat(CollectionErrorPolicy.nextRetry(failure,99,now)).isEqualTo(now.plusHours(24));
    }
    @Test void quotaWaitsForReportedResetOrDailyFallback() {
        var code=PlaceRefinementErrorCode.AI_API_QUOTA_EXHAUSTED;
        assertThat(CollectionErrorPolicy.nextRetry(new CollectionFailure(code,"test",now.plusHours(3)),0,now)).isEqualTo(now.plusHours(3).plusMinutes(1));
        assertThat(CollectionErrorPolicy.nextRetry(new CollectionFailure(code,"test"),0,now)).isEqualTo(now.plusHours(24));
    }
    @Test void credentialsAndAmbiguousReceiptNeverAutomaticallyResubmit() {
        for(var code: new PlaceRefinementErrorCode[]{PlaceRefinementErrorCode.AI_API_AUTH_FAILED,
                PlaceRefinementErrorCode.AI_API_INVALID_REQUEST,PlaceRefinementErrorCode.OPENAI_BATCH_SUBMISSION_UNKNOWN,
                PlaceRefinementErrorCode.AI_RESPONSE_REFUSED})
            assertThat(CollectionErrorPolicy.nextRetry(new CollectionFailure(code,"test"),0,now)).isNull();
    }
    @Test void naverKeepsExistingThreeAttemptLimit() {
        assertThat(CollectionErrorPolicy.nextRetry(new CollectionFailure(PlaceRefinementErrorCode.NAVER_API_TEMPORARY_FAILURE,"test"),3,now)).isNull();
    }
    @Test void quotaDoesNotConfuseOrdinary429WithUsageExhaustion() throws Exception {
        var json=new ObjectMapper();
        assertThat(CollectionFailureClassifier.http("AI",429,json.readTree("{\"error\":{\"code\":\"rate_limit_exceeded\"}}"),null).code()).isEqualTo(PlaceRefinementErrorCode.AI_API_RATE_LIMITED);
        assertThat(CollectionFailureClassifier.http("AI",429,json.readTree("{\"error\":{\"code\":\"usage_limit_reached\",\"resets_at\":4102444800}}"),null).retryHint()).isNotNull();
    }
}
