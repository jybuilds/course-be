package com.junsang.course_backend.domain.place.collection.pipeline.batch.ai.entity;

import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/// OpenAI Batch API 제출과 결과 반영 상태를 보관한다.
@Entity
@Table(name = "openai_batch_tagging_jobs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class OpenAiBatchTaggingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "openai_batch_id", unique = true, length = 100)
    private String openAiBatchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OpenAiBatchTaggingStatus status;

    @Column(name = "requested_count", nullable = false)
    private int requestedCount;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_code", length = 100)
    private PlaceRefinementErrorCode errorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // OpenAI 제출 전 Temp를 선점할 로컬 Job을 만든다.
    public static OpenAiBatchTaggingJob submitting(int requestedCount) {
        OpenAiBatchTaggingJob job = new OpenAiBatchTaggingJob();
        job.status = OpenAiBatchTaggingStatus.SUBMITTING;
        job.requestedCount = requestedCount;
        return job;
    }

    // OpenAI가 반환한 Batch ID를 기록하고 결과 대기 상태로 전환한다.
    public void submit(String openAiBatchId) {
        this.openAiBatchId = openAiBatchId;
        status = OpenAiBatchTaggingStatus.SUBMITTED;
        errorCode = null;
        errorMessage = null;
    }

    // 결과 저장이 끝난 Job을 완료 처리한다.
    public void complete() {
        status = OpenAiBatchTaggingStatus.COMPLETED;
        errorCode = null;
        errorMessage = null;
    }

    // 외부 제출 또는 결과 처리 실패 원인을 보관한다.
    public void fail(PlaceRefinementErrorCode errorCode, String errorMessage) {
        status = OpenAiBatchTaggingStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
