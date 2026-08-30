package com.junsang.course_backend.domain.place.entity;

import com.junsang.course_backend.domain.place.collection.CollectionJobStatus;
import com.junsang.course_backend.domain.place.collection.CollectionSearchType;
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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/// Area·수집 조건·rect 단위의 중단 가능한 카카오 수집 작업이다.
@Entity
@Table(name = "place_collection_jobs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class PlaceCollectionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_job_id")
    private PlaceCollectionJob parentJob;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private Area area;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_profile_id", nullable = false)
    private PlaceCollectionProfile collectionProfile;

    @Column(name = "profile_code", nullable = false, length = 50)
    private String profileCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "search_type", nullable = false, length = 20)
    private CollectionSearchType searchType;

    @Column(name = "category_group_code", length = 10)
    private String categoryGroupCode;

    @Column(length = 200)
    private String query;

    @Enumerated(EnumType.STRING)
    @Column(name = "place_type", nullable = false, length = 20)
    private PlaceType placeType;

    @Column(name = "min_latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal minLatitude;

    @Column(name = "min_longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal minLongitude;

    @Column(name = "max_latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal maxLatitude;

    @Column(name = "max_longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal maxLongitude;

    @Column(nullable = false)
    private int depth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CollectionJobStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    public static PlaceCollectionJob initial(Area area, PlaceCollectionProfile profile) {
        return create(null, area, profile, area.getCollectionMinLatitude(), area.getCollectionMinLongitude(),
                area.getCollectionMaxLatitude(), area.getCollectionMaxLongitude(), 0);
    }

    private static PlaceCollectionJob create(PlaceCollectionJob parent, Area area, PlaceCollectionProfile profile, BigDecimal minLat, BigDecimal minLon, BigDecimal maxLat, BigDecimal maxLon, int depth) {
        PlaceCollectionJob job = new PlaceCollectionJob();
        job.parentJob = parent;
        job.area = area;
        job.collectionProfile = profile;
        job.profileCode = profile.getCode();
        job.searchType = profile.getSearchType();
        job.categoryGroupCode = profile.getCategoryGroupCode();
        job.query = profile.getQuery();
        job.placeType = profile.getPlaceType();
        job.minLatitude = minLat;
        job.minLongitude = minLon;
        job.maxLatitude = maxLat;
        job.maxLongitude = maxLon;
        job.depth = depth;
        job.status = CollectionJobStatus.READY;
        return job;
    }

    // 현재 Job의 조건 스냅샷으로 네 개의 하위 rect Job을 만든다.
    public List<PlaceCollectionJob> split() {
        BigDecimal midLat = minLatitude.add(maxLatitude).divide(BigDecimal.valueOf(2));
        BigDecimal midLon = minLongitude.add(maxLongitude).divide(BigDecimal.valueOf(2));
        status = CollectionJobStatus.SPLIT;
        completedAt = LocalDateTime.now();
        return List.of(child(minLatitude, minLongitude, midLat, midLon), child(minLatitude, midLon, midLat, maxLongitude), child(midLat, minLongitude, maxLatitude, midLon), child(midLat, midLon, maxLatitude, maxLongitude));
    }

    private PlaceCollectionJob child(BigDecimal minLat, BigDecimal minLon, BigDecimal maxLat, BigDecimal maxLon) {
        PlaceCollectionJob child = new PlaceCollectionJob();
        child.parentJob = this;
        child.area = area;
        child.collectionProfile = collectionProfile;
        child.profileCode = profileCode;
        child.searchType = searchType;
        child.categoryGroupCode = categoryGroupCode;
        child.query = query;
        child.placeType = placeType;
        child.minLatitude = minLat;
        child.minLongitude = minLon;
        child.maxLatitude = maxLat;
        child.maxLongitude = maxLon;
        child.depth = depth + 1;
        child.status = CollectionJobStatus.READY;
        return child;
    }

    public void start() {
        status = CollectionJobStatus.RUNNING;
        startedAt = LocalDateTime.now();
        errorMessage = null;
    }

    public void complete() {
        status = CollectionJobStatus.COMPLETED;
        completedAt = LocalDateTime.now();
    }

    public void fail(String message) {
        status = CollectionJobStatus.FAILED;
        errorMessage = message;
        completedAt = LocalDateTime.now();
    }
}
