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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/// 세부 지역이 어떤 활동과 분위기를 대표하는지 연결하는 태그다.
@Entity
@Table(name = "area_tags")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class AreaTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private Area area;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    @Column(nullable = false)
    private int weight;

    @Column(name = "recommendation_rank")
    private Integer recommendationRank;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommend_visit_places", columnDefinition = "jsonb")
    private List<String> recommendVisitPlaces;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attraction_features", columnDefinition = "jsonb")
    private List<String> attractionFeatures;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "differentiators", columnDefinition = "jsonb")
    private List<String> differentiators;
}
