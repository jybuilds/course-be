package com.junsang.course_backend.domain.place.collection.entity;

import com.junsang.course_backend.domain.place.entity.Area;
import com.junsang.course_backend.domain.place.entity.PlaceProvider;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/// 카카오 수집부터 AI 태깅 직전까지 장소 정제 상태와 원본 정보를 보관한다.
@Entity
@Table(name = "place_collection_temp")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class PlaceCollectionTemp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private Area area;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PlaceProvider provider;

    @Column(name = "provider_place_id", nullable = false, length = 100)
    private String providerPlaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_place_type", nullable = false, length = 20)
    private PlaceType defaultPlaceType;

    @Column(name = "place_type_finalized", nullable = false)
    private boolean placeTypeFinalized;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "address_name", length = 500)
    private String addressName;

    @Column(name = "road_address_name", length = 500)
    private String roadAddressName;

    @Column(name = "kakao_category_name", length = 500)
    private String kakaoCategoryName;

    @Column(name = "kakao_category_group_code", length = 20)
    private String kakaoCategoryGroupCode;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "kakao_place_url", length = 1000)
    private String kakaoPlaceUrl;

    @Column(length = 50)
    private String phone;

    @Column(name = "naver_title", length = 200)
    private String naverTitle;

    @Column(name = "naver_search_url", length = 1000)
    private String naverSearchUrl;

    @Column(name = "naver_category_name", length = 500)
    private String naverCategoryName;

    @Column(name = "naver_address_name", length = 500)
    private String naverAddressName;

    @Column(name = "naver_road_address_name", length = 500)
    private String naverRoadAddressName;

    @Column(name = "naver_matched_at")
    private LocalDateTime naverMatchedAt;

    @Column(name = "naver_blog_evidence", columnDefinition = "TEXT")
    private String naverBlogEvidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "naver_blog_status", length = 20)
    private NaverBlogCollectionStatus naverBlogStatus;

    @Column(name = "naver_blog_error_message", length = 1000)
    private String naverBlogErrorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "naver_blog_error_code", length = 100)
    // 블로그는 보조 자료이므로 정제 상태와 분리해 실패 원인만 보관한다.
    private PlaceRefinementErrorCode naverBlogErrorCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_step", nullable = false, length = 30)
    private PlaceCollectionStep processingStep;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlaceCollectionTempStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_code", length = 100)
    private PlaceRefinementErrorCode errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "ai_batch_job_id")
    private Long aiBatchJobId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 카카오 원본 정보로 네이버 정제 대기 데이터를 생성한다.
    public static PlaceCollectionTemp create(
            Area area,
            PlaceProvider provider,
            String providerPlaceId,
            PlaceType defaultPlaceType,
            String name,
            String addressName,
            String roadAddressName,
            String kakaoCategoryName,
            String kakaoCategoryGroupCode,
            BigDecimal latitude,
            BigDecimal longitude,
            String kakaoPlaceUrl,
            String phone
    ) {
        return create(
                area,
                provider,
                providerPlaceId,
                defaultPlaceType,
                false,
                name,
                addressName,
                roadAddressName,
                kakaoCategoryName,
                kakaoCategoryGroupCode,
                latitude,
                longitude,
                kakaoPlaceUrl,
                phone
        );
    }

    // 카카오 원본과 Type 확정 여부를 함께 저장해 네이버 정제를 시작한다.
    public static PlaceCollectionTemp create(
            Area area,
            PlaceProvider provider,
            String providerPlaceId,
            PlaceType defaultPlaceType,
            boolean placeTypeFinalized,
            String name,
            String addressName,
            String roadAddressName,
            String kakaoCategoryName,
            String kakaoCategoryGroupCode,
            BigDecimal latitude,
            BigDecimal longitude,
            String kakaoPlaceUrl,
            String phone
    ) {
        PlaceCollectionTemp temp = new PlaceCollectionTemp();
        temp.area = area;
        temp.provider = provider;
        temp.providerPlaceId = providerPlaceId;
        temp.defaultPlaceType = defaultPlaceType;
        temp.placeTypeFinalized = placeTypeFinalized;
        temp.name = name;
        temp.addressName = addressName;
        temp.roadAddressName = roadAddressName;
        temp.kakaoCategoryName = kakaoCategoryName;
        temp.kakaoCategoryGroupCode = kakaoCategoryGroupCode;
        temp.latitude = latitude;
        temp.longitude = longitude;
        temp.kakaoPlaceUrl = kakaoPlaceUrl;
        temp.phone = phone;
        temp.processingStep = PlaceCollectionStep.NAVER_ENRICHMENT;
        temp.status = PlaceCollectionTempStatus.PENDING;
        return temp;
    }

    // 재수집한 카카오 원본만 갱신하고 현재 정제 단계와 상태는 유지한다.
    public void refreshKakao(
            Area area,
            PlaceType defaultPlaceType,
            boolean placeTypeFinalized,
            String name,
            String addressName,
            String roadAddressName,
            String kakaoCategoryName,
            String kakaoCategoryGroupCode,
            BigDecimal latitude,
            BigDecimal longitude,
            String kakaoPlaceUrl,
            String phone
    ) {
        this.area = area;
        this.defaultPlaceType = defaultPlaceType;
        this.placeTypeFinalized = placeTypeFinalized;
        this.name = name;
        this.addressName = addressName;
        this.roadAddressName = roadAddressName;
        this.kakaoCategoryName = kakaoCategoryName;
        this.kakaoCategoryGroupCode = kakaoCategoryGroupCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.kakaoPlaceUrl = kakaoPlaceUrl;
        this.phone = phone;
    }

    // 네이버 정제 요청을 시작하고 시도 횟수와 시각을 기록한다.
    public void startProcessing() {
        requireNaverStep();
        if (status != PlaceCollectionTempStatus.PENDING
                && status != PlaceCollectionTempStatus.FAILED) {
            throw new IllegalStateException("네이버 정제를 시작할 수 없는 상태입니다.");
        }
        status = PlaceCollectionTempStatus.PROCESSING;
        attemptCount++;
        lastAttemptAt = LocalDateTime.now();
        clearError();
    }

    // 매칭된 네이버 정보를 저장하고 AI 태깅 대기로 전환한다.
    public void completeNaverEnrichment(
            PlaceType placeType,
            String title,
            String searchUrl,
            String category,
            String address,
            String roadAddress,
            String blogEvidence,
            NaverBlogCollectionStatus blogStatus,
            String blogErrorMessage
    ) {
        completeNaverEnrichment(
                placeType,
                false,
                title,
                searchUrl,
                category,
                address,
                roadAddress,
                blogEvidence,
                blogStatus,
                null,
                blogErrorMessage
        );
    }

    // 블로그 오류 코드까지 함께 저장해야 하는 수집 파이프라인용 완료 처리다.
    public void completeNaverEnrichment(
            PlaceType placeType,
            String title,
            String searchUrl,
            String category,
            String address,
            String roadAddress,
            String blogEvidence,
            NaverBlogCollectionStatus blogStatus,
            PlaceRefinementErrorCode blogErrorCode,
            String blogErrorMessage
    ) {
        completeNaverEnrichment(
                placeType,
                false,
                title,
                searchUrl,
                category,
                address,
                roadAddress,
                blogEvidence,
                blogStatus,
                blogErrorCode,
                blogErrorMessage
        );
    }

    // 매칭된 네이버 정보와 Type 확정 여부를 저장하고 AI 태깅 대기로 전환한다.
    public void completeNaverEnrichment(
            PlaceType placeType,
            boolean placeTypeFinalized,
            String title,
            String searchUrl,
            String category,
            String address,
            String roadAddress,
            String blogEvidence,
            NaverBlogCollectionStatus blogStatus,
            PlaceRefinementErrorCode blogErrorCode,
            String blogErrorMessage
    ) {
        requireNaverStep();
        if (status != PlaceCollectionTempStatus.PROCESSING) {
            throw new IllegalStateException("처리 중인 장소만 네이버 정제를 완료할 수 있습니다.");
        }
        naverTitle = title;
        naverSearchUrl = searchUrl;
        naverCategoryName = category;
        naverAddressName = address;
        naverRoadAddressName = roadAddress;
        naverBlogEvidence = blogEvidence;
        naverBlogStatus = blogStatus;
        naverBlogErrorCode = blogErrorCode;
        naverBlogErrorMessage = blogErrorMessage;
        naverMatchedAt = LocalDateTime.now();
        defaultPlaceType = placeType;
        this.placeTypeFinalized = placeTypeFinalized;
        processingStep = PlaceCollectionStep.AI_TAGGING;
        status = PlaceCollectionTempStatus.PENDING;
        clearError();
    }

    // 이름·카테고리가 유사하지만 주소가 다른 네이버 후보를 검토용으로 남긴다.
    public void recordPossibleRelocation(
            String title,
            String searchUrl,
            String category,
            String address,
            String roadAddress
    ) {
        requireNaverStep();
        if (status != PlaceCollectionTempStatus.PROCESSING) {
            throw new IllegalStateException("처리 중인 장소만 이전 의심 후보를 기록할 수 있습니다.");
        }
        naverTitle = title;
        naverSearchUrl = searchUrl;
        naverCategoryName = category;
        naverAddressName = address;
        naverRoadAddressName = roadAddress;
        fail(
                PlaceRefinementErrorCode.NAVER_POSSIBLE_RELOCATION,
                "이름과 카테고리가 유사하지만 카카오와 네이버 주소가 다릅니다."
        );
    }

    // AI 태깅을 시작하고 재시도 횟수와 시각을 기록한다.
    public void startAiTagging() {
        requireAiStep();
        if (status != PlaceCollectionTempStatus.PENDING
                && status != PlaceCollectionTempStatus.FAILED) {
            throw new IllegalStateException("AI 태깅을 시작할 수 없는 상태입니다.");
        }
        status = PlaceCollectionTempStatus.PROCESSING;
        attemptCount++;
        lastAttemptAt = LocalDateTime.now();
        clearError();
    }

    // OpenAI Batch Job에 묶인 AI 태깅 대상을 처리 중으로 선점한다.
    public void startAiBatchTagging(Long aiBatchJobId) {
        startAiTagging();
        this.aiBatchJobId = aiBatchJobId;
    }

    // 최종 Place와 태그 저장이 끝난 데이터를 완료 처리한다.
    public void completeAiTagging() {
        requireAiStep();
        if (status != PlaceCollectionTempStatus.PROCESSING) {
            throw new IllegalStateException("처리 중인 장소만 AI 태깅을 완료할 수 있습니다.");
        }
        status = PlaceCollectionTempStatus.COMPLETED;
        aiBatchJobId = null;
        clearError();
    }

    // 확정되지 않은 타입만 AI의 네이버 카테고리 판정으로 확정한다.
    public PlaceType applyAiPlaceType(PlaceType placeType) {
        if (placeTypeFinalized) {
            return defaultPlaceType;
        }
        if (placeType == null) {
            throw new IllegalArgumentException("AI PlaceType이 비어 있습니다.");
        }
        defaultPlaceType = placeType;
        placeTypeFinalized = true;
        return defaultPlaceType;
    }

    // 현재 단계의 실패 원인을 기록하고 재시도 가능한 상태로 남긴다.
    public void fail(PlaceRefinementErrorCode errorCode, String errorMessage) {
        status = PlaceCollectionTempStatus.FAILED;
        aiBatchJobId = null;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    // 실패한 현재 단계를 다시 대기 상태로 되돌린다.
    public void retry() {
        if (status != PlaceCollectionTempStatus.FAILED) {
            throw new IllegalStateException("실패한 장소만 재시도할 수 있습니다.");
        }
        status = PlaceCollectionTempStatus.PENDING;
        clearError();
    }

    // 네이버 정제 단계인지 검증한다.
    private void requireNaverStep() {
        if (processingStep != PlaceCollectionStep.NAVER_ENRICHMENT) {
            throw new IllegalStateException("네이버 정제 단계가 아닙니다.");
        }
    }

    // AI 태깅 단계인지 검증한다.
    private void requireAiStep() {
        if (processingStep != PlaceCollectionStep.AI_TAGGING) {
            throw new IllegalStateException("AI 태깅 단계가 아닙니다.");
        }
    }

    // 이전 실패 정보를 제거한다.
    private void clearError() {
        errorCode = null;
        errorMessage = null;
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
