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

/// 장소가 제공하는 활동, 음식, 분위기 특성을 연결하는 태그다.
@Entity
@Table(name = "place_tags")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class PlaceTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    @Column(nullable = false)
    private int weight;

    // 검증된 AI 태깅 결과로 장소와 태그를 연결한다.
    public static PlaceTag create(Place place, Tag tag, int weight) {
        if (weight < 0 || weight > 100) {
            throw new IllegalArgumentException("태그 가중치는 0에서 100 사이여야 합니다.");
        }
        PlaceTag placeTag = new PlaceTag();
        placeTag.place = place;
        placeTag.tag = tag;
        placeTag.weight = weight;
        return placeTag;
    }
}
