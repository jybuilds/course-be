package com.junsang.course_backend.domain.place.collection.pipeline.common.naver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.junsang.course_backend.domain.place.entity.Area;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.domain.place.entity.PlaceProvider;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import com.junsang.course_backend.infra.naver.dto.response.NaverLocalSearchResponse.NaverLocalItem;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class NaverPlaceMatcherTest {

    private final NaverPlaceMatcher matcher = new NaverPlaceMatcher(new NaverPlaceNormalizer());

    @Test
    void selectsSameNameAndAddressMatch() {
        NaverLocalItem expected = item(
                "<b>성심당</b> 본점",
                "대전광역시 중구 은행동 145",
                "대전광역시 중구 대종로480번길 15"
        );

        NaverLocalItem matched = matcher.match(createTemp(), List.of(
                item("성심당 다른 지점", "대전 중구 오류동 1", "대전 중구 오류로 1"),
                expected
        ));

        assertThat(matched).isSameAs(expected);
    }

    @Test
    void selectsNameWhenSameAddressHasMultiplePlaces() {
        NaverLocalItem expected = item(
                "<b>성심당</b> 본점",
                "대전 중구 은행동 145",
                "대전 중구 대종로480번길 15"
        );

        NaverLocalItem matched = matcher.match(createTemp(), List.of(
                item("다른 업체", "대전 중구 은행동 145", "대전 중구 대종로480번길 15"),
                expected
        ));

        assertThat(matched).isSameAs(expected);
    }

    @Test
    void acceptsBuildingNameDifferenceWhenRoadAddressMatches() {
        PlaceCollectionTemp temp = PlaceCollectionTemp.create(
                Mockito.mock(Area.class),
                PlaceProvider.KAKAO,
                "2",
                PlaceType.CAFE,
                "설빙 홍대입구역점",
                "서울 마포구 동교동 162-3",
                "서울 마포구 홍익로6길 15",
                "카페",
                "CE7",
                new BigDecimal("37.5553744"),
                new BigDecimal("126.9227117"),
                "https://place.map.kakao.com/2",
                "02-000-0000"
        );
        NaverLocalItem expected = new NaverLocalItem(
                "<b>설빙 홍대입구역점</b>",
                "",
                "카페,디저트>빙수",
                "",
                "",
                "서울특별시 마포구 동교동 162-3 삼주빌딩",
                "서울특별시 마포구 홍익로6길 15 삼주빌딩",
                "1269227519",
                "375553783"
        );

        assertThat(matcher.match(temp, List.of(expected))).isSameAs(expected);
    }

    @Test
    void selectsSingleRoadAddressCandidateWhenNamesUseDifferentScripts() {
        PlaceCollectionTemp temp = PlaceCollectionTemp.create(
                Mockito.mock(Area.class),
                PlaceProvider.KAKAO,
                "3",
                PlaceType.CAFE,
                "로우프",
                "서울 마포구 합정동 393-25",
                "서울 마포구 포은로 14",
                "카페",
                "CE7",
                new BigDecimal("37.5490000"),
                new BigDecimal("126.9130000"),
                "https://place.map.kakao.com/3",
                "02-000-0000"
        );
        NaverLocalItem expected = item(
                "LOAF COFFEE",
                "서울특별시 마포구 합정동 393-18",
                "서울특별시 마포구 포은로 14 수성빌딩 1층 LOAF COFFEE"
        );

        assertThat(matcher.match(temp, List.of(expected))).isSameAs(expected);
    }

    @Test
    void selectsCategoryWhenSameNameAndAddressHaveMultipleCandidates() {
        PlaceCollectionTemp temp = PlaceCollectionTemp.create(
                Mockito.mock(Area.class),
                PlaceProvider.KAKAO,
                "4",
                PlaceType.CAFE,
                "필름인필터",
                "서울 마포구 합정동 388-20",
                "서울 마포구 월드컵로3길 31-32",
                "음식점 > 카페",
                "CE7",
                new BigDecimal("37.5498051"),
                new BigDecimal("126.9109268"),
                "https://place.map.kakao.com/4",
                "02-6953-0208"
        );
        NaverLocalItem expected = item(
                "필름인필터",
                "카페,디저트>카페",
                "서울특별시 마포구 합정동 388-20",
                "서울특별시 마포구 월드컵로3길 31-32"
        );
        NaverLocalItem studio = item(
                "필름인필터 스튜디오",
                "사진,스튜디오",
                "서울특별시 마포구 합정동 388-20",
                "서울특별시 마포구 월드컵로3길 31-32"
        );

        assertThat(matcher.match(temp, List.of(expected, studio))).isSameAs(expected);
    }

    @Test
    void rejectsMissingAndAmbiguousMatches() {
        assertThatThrownBy(() -> matcher.match(createTemp(), List.of()))
                .isInstanceOfSatisfying(PlaceRefinementException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(PlaceRefinementErrorCode.NAVER_RESULT_NOT_FOUND));

        assertThatThrownBy(() -> matcher.match(createTemp(), List.of(
                item("성심당 본점", "대전 중구 은행동 145", "대전 중구 대종로480번길 15"),
                item("성심당 본점", "대전 중구 은행동 145", "대전 중구 대종로480번길 15")
        )))
                .isInstanceOfSatisfying(PlaceRefinementException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(PlaceRefinementErrorCode.NAVER_PLACE_AMBIGUOUS));
    }

    private PlaceCollectionTemp createTemp() {
        return PlaceCollectionTemp.create(
                Mockito.mock(Area.class),
                PlaceProvider.KAKAO,
                "1",
                PlaceType.CAFE,
                "성심당 본점",
                "대전 중구 은행동 145",
                "대전 중구 대종로480번길 15",
                "카페",
                "CE7",
                new BigDecimal("36.3275000"),
                new BigDecimal("127.4277000"),
                "https://place.map.kakao.com/1",
                "042-000-0000"
        );
    }

    private NaverLocalItem item(String title, String address, String roadAddress) {
        return item(title, "카페,디저트>베이커리", address, roadAddress);
    }

    private NaverLocalItem item(String title, String category, String address, String roadAddress) {
        return new NaverLocalItem(
                title,
                "https://example.com",
                category,
                "",
                "",
                address,
                roadAddress,
                "1274277000",
                "363275000"
        );
    }
}
