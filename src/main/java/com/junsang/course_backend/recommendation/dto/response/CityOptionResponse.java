package com.junsang.course_backend.recommendation.dto.response;

import com.junsang.course_backend.domain.place.entity.City;

/// 프론트에 제공할 도시 선택지다.
public record CityOptionResponse(
        Long id,
        String code,
        String name
) {
    public static CityOptionResponse from(City city) {
        return new CityOptionResponse(city.getId(), city.getCode(), city.getName());
    }
}
