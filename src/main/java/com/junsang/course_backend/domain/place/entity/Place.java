package com.junsang.course_backend.domain.place.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/// 외부 API에서 수집·정제한 장소 Table
@Entity
@Table(name = "places")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PlaceProvider provider;

    @Column(name = "provider_place_id", nullable = false, length = 100)
    private String providerPlaceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private Area area;

    @Enumerated(EnumType.STRING)
    @Column(name = "place_type", nullable = false, length = 20)
    private PlaceType placeType;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "address_name", length = 500)
    private String addressName;

    @Column(name = "road_address_name", length = 500)
    private String roadAddressName;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "place_url", length = 1000)
    private String placeUrl;

    @Column(length = 50)
    private String phone;

    @Column(name = "is_anchor_candidate", nullable = false)
    private boolean isAnchorCandidate;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "operating_hours", length = 1000)
    private String operatingHours;

    @Column(name = "operating_days", length = 500)
    private String operatingDays;

    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Place create(
            PlaceProvider provider,
            String providerPlaceId,
            Area area,
            PlaceType placeType,
            String name,
            String addressName,
            String roadAddressName,
            BigDecimal latitude,
            BigDecimal longitude,
            String placeUrl,
            String phone,
            boolean isAnchorCandidate,
            String operatingHours,
            String operatingDays
    ) {
        Place place = new Place();
        place.provider = provider;
        place.providerPlaceId = providerPlaceId;
        place.area = area;
        place.placeType = placeType;
        place.name = name;
        place.addressName = addressName;
        place.roadAddressName = roadAddressName;
        place.latitude = latitude;
        place.longitude = longitude;
        place.placeUrl = placeUrl;
        place.phone = phone;
        place.isAnchorCandidate = isAnchorCandidate;
        place.operatingHours = operatingHours;
        place.operatingDays = operatingDays;
        place.isActive = true;
        return place;
    }

    // 같은 외부 장소가 다시 수집되면 서비스 판단값은 보존하고 제공자 정보만 최신화한다.
    public void refresh(Area area, PlaceType placeType, String name, String addressName, String roadAddressName,
                        BigDecimal latitude, BigDecimal longitude, String placeUrl, String phone) {
        this.area = area; this.placeType = placeType; this.name = name; this.addressName = addressName;
        this.roadAddressName = roadAddressName; this.latitude = latitude; this.longitude = longitude;
        this.placeUrl = placeUrl; this.phone = phone; this.lastSyncedAt = LocalDateTime.now();
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        lastSyncedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
