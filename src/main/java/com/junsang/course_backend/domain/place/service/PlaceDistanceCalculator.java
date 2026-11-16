package com.junsang.course_backend.domain.place.service;

import com.junsang.course_backend.domain.place.entity.Place;

import java.math.BigDecimal;

/// 두 좌표 사이의 직선거리를 미터로 계산한다.
public final class PlaceDistanceCalculator {
    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private PlaceDistanceCalculator() {
    }

    public static double between(Place origin, Place target) {
        return between(
                origin.getLatitude(), origin.getLongitude(),
                target.getLatitude(), target.getLongitude()
        );
    }

    // 하버사인 공식으로 지구 곡률을 반영한 직선거리를 구한다.
    public static double between(
            BigDecimal originLatitude,
            BigDecimal originLongitude,
            BigDecimal targetLatitude,
            BigDecimal targetLongitude
    ) {
        double latitudeDistance = Math.toRadians(targetLatitude.subtract(originLatitude).doubleValue());
        double longitudeDistance = Math.toRadians(targetLongitude.subtract(originLongitude).doubleValue());
        double originLatitudeRadian = Math.toRadians(originLatitude.doubleValue());
        double targetLatitudeRadian = Math.toRadians(targetLatitude.doubleValue());
        double haversine = Math.sin(latitudeDistance / 2) * Math.sin(latitudeDistance / 2)
                + Math.cos(originLatitudeRadian) * Math.cos(targetLatitudeRadian)
                * Math.sin(longitudeDistance / 2) * Math.sin(longitudeDistance / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
    }
}
