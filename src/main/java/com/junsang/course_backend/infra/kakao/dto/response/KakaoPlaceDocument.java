package com.junsang.course_backend.infra.kakao.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoPlaceDocument(
        String id,
        @JsonProperty("place_name") String placeName,
        @JsonProperty("category_name") String categoryName,
        @JsonProperty("address_name") String addressName,
        @JsonProperty("road_address_name") String roadAddressName,
        String phone,
        @JsonProperty("category_group_code") String categoryGroupCode,
        @JsonProperty("category_group_name") String categoryGroupName,
        BigDecimal x,
        BigDecimal y,
        @JsonProperty("place_url") String placeUrl,
        String distance
) {
}
