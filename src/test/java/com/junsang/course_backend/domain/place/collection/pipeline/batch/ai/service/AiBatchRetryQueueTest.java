package com.junsang.course_backend.domain.place.collection.pipeline.batch.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionStep;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTempStatus;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AiBatchRetryQueueTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlaceCollectionTempRepository repository;

    @Test
    void retriesAfterShortOrLongDelayWithoutSelectingAmbiguousSubmissions() {
        LocalDateTime now = LocalDateTime.now();
        insert("retry-short", "FAILED", "OPENAI_BATCH_TEMPORARY_FAILURE", 2, now.minusMinutes(6));
        insert("retry-too-early", "FAILED", "OPENAI_BATCH_TEMPORARY_FAILURE", 2, now.minusMinutes(4));
        insert("retry-long", "FAILED", "OPENAI_BATCH_TEMPORARY_FAILURE", 3, now.minusHours(49));
        insert("retry-long-too-early", "FAILED", "OPENAI_BATCH_TEMPORARY_FAILURE", 3, now.minusHours(47));
        insert("retry-unknown", "FAILED", "OPENAI_BATCH_SUBMISSION_UNKNOWN", 3, now.minusDays(7));
        insert("retry-pending", "PENDING", null, 0, now.minusDays(7));

        List<PlaceCollectionTemp> targets = repository.findAiBatchSubmissionTargets(
                PlaceCollectionStep.AI_TAGGING,
                PlaceCollectionTempStatus.PENDING,
                PlaceCollectionTempStatus.FAILED,
                List.of(PlaceRefinementErrorCode.OPENAI_BATCH_TEMPORARY_FAILURE),
                now.minusMinutes(5),
                now.minusHours(48),
                true,
                PageRequest.of(0, 100)
        );

        assertThat(targets).extracting(PlaceCollectionTemp::getProviderPlaceId)
                .containsExactly("retry-short", "retry-long");
        Long jobId = jdbc.queryForObject(
                "INSERT INTO openai_batch_tagging_jobs (status, requested_count) VALUES ('SUBMITTING', 2) RETURNING id",
                Long.class
        );
        targets.forEach(temp -> temp.startAiBatchTagging(jobId));
        assertThat(targets).extracting(PlaceCollectionTemp::getAiRetryCount).containsExactly(3, 3);
    }

    private void insert(String providerId, String status, String errorCode, int retryCount, LocalDateTime lastAttempt) {
        jdbc.update("""
                INSERT INTO place_collection_temp
                    (area_id, provider, provider_place_id, default_place_type, name, latitude, longitude,
                     processing_step, status, error_code, ai_retry_count, last_attempt_at)
                SELECT id, 'KAKAO', ?, 'CAFE', ?, 37.55, 126.95, 'AI_TAGGING', ?, ?, ?, ?
                FROM areas ORDER BY id LIMIT 1
                """, providerId, providerId, status, errorCode, retryCount, lastAttempt);
    }
}
