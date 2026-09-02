package com.junsang.course_backend.domain.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/// 도시 안에서 추천을 시작할 수 있는 세부 지역이다. 예: 성수, 서촌, 용산
@Entity
@Table(name = "areas")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Area {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal collectionCenterLatitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal collectionCenterLongitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal collectionMinLatitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal collectionMinLongitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal collectionMaxLatitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal collectionMaxLongitude;

}
