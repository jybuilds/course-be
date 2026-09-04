package com.junsang.course_backend.domain.place.collection.pipeline.common.naver.service;

import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.infra.naver.dto.response.NaverLocalSearchResponse.NaverLocalItem;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 네이버 지역 검색 후보 중 카카오 장소와 주소가 같은 한 곳을 선택한다.
@Component
@RequiredArgsConstructor
public class NaverPlaceMatcher {

    private final NaverPlaceNormalizer normalizer;

    // 도로명주소를 우선 비교하고 지번주소를 보조로 사용해 같은 장소를 고른다.
    public NaverLocalItem match(PlaceCollectionTemp temp, List<NaverLocalItem> items) {
        if (items == null || items.isEmpty()) {
            throw new PlaceRefinementException(
                    PlaceRefinementErrorCode.NAVER_RESULT_NOT_FOUND,
                    "네이버 지역 검색 결과가 없습니다."
            );
        }

        List<NaverLocalItem> roadAddressMatches = items.stream()
                .filter(item -> matches(temp.getRoadAddressName(), item.roadAddress()))
                .toList();
        NaverLocalItem roadAddressMatch = selectPreferred(temp, roadAddressMatches, "도로명주소");
        if (roadAddressMatch != null) {
            return roadAddressMatch;
        }

        List<NaverLocalItem> addressMatches = items.stream()
                .filter(item -> matches(temp.getAddressName(), item.address()))
                .toList();
        NaverLocalItem addressMatch = selectPreferred(temp, addressMatches, "지번주소");
        if (addressMatch != null) {
            return addressMatch;
        }

        throw new PlaceRefinementException(
                PlaceRefinementErrorCode.NAVER_PLACE_NOT_MATCHED,
                "도로명주소 또는 지번주소가 일치하는 네이버 장소가 없습니다."
        );
    }

    // 이름·카테고리는 유사하지만 주소가 다른 후보가 하나면 이전 또는 다른 지점 의심으로 반환한다.
    public NaverLocalItem findPossibleRelocation(PlaceCollectionTemp temp, List<NaverLocalItem> items) {
        if (items == null) {
            return null;
        }
        List<NaverLocalItem> candidates = items.stream()
                .filter(item -> hasSimilarName(temp, item))
                .filter(item -> hasMatchingCategory(temp, item))
                .filter(item -> !matches(temp.getRoadAddressName(), item.roadAddress()))
                .filter(item -> !matches(temp.getAddressName(), item.address()))
                .toList();
        return candidates.size() == 1 ? candidates.getFirst() : null;
    }

    // 같은 주소 후보는 상호명, 카테고리 순으로 좁히고 끝까지 하나가 아니면 선택하지 않는다.
    private NaverLocalItem selectPreferred(
            PlaceCollectionTemp temp,
            List<NaverLocalItem> addressMatches,
            String condition
    ) {
        List<NaverLocalItem> nameMatches = addressMatches.stream()
                .filter(item -> hasSimilarName(temp, item))
                .toList();
        if (nameMatches.size() == 1) {
            return nameMatches.getFirst();
        }
        if (nameMatches.size() > 1) {
            NaverLocalItem categoryMatch = selectSingle(
                    nameMatches.stream().filter(item -> hasMatchingCategory(temp, item)).toList(),
                    condition + "·카테고리"
            );
            if (categoryMatch != null) {
                return categoryMatch;
            }
            return selectSingle(nameMatches, condition);
        }
        return selectSingle(addressMatches, condition);
    }

    // 카카오와 네이버 상세 카테고리의 공통 항목이 하나라도 있는지 확인한다.
    private boolean hasMatchingCategory(PlaceCollectionTemp temp, NaverLocalItem item) {
        String kakaoCategory = temp.getKakaoCategoryName();
        String naverCategory = normalizer.normalizeName(item.category());
        return kakaoCategory != null && !kakaoCategory.isBlank()
                && !naverCategory.isEmpty()
                && Arrays.stream(kakaoCategory.split("[>,]"))
                .map(normalizer::normalizeName)
                .filter(token -> token.length() > 1)
                .anyMatch(naverCategory::contains);
    }

    // 공백·특수문자·지점 표기 차이를 허용해 장소명이 유사한지 확인한다.
    private boolean hasSimilarName(PlaceCollectionTemp temp, NaverLocalItem item) {
        String kakaoName = removeBranchSuffix(normalizer.normalizeName(temp.getName()));
        String naverName = removeBranchSuffix(normalizer.normalizeName(item.title()));
        return !kakaoName.isEmpty() && !naverName.isEmpty()
                && (kakaoName.equals(naverName)
                || kakaoName.contains(naverName)
                || naverName.contains(kakaoName));
    }

    // 지점 표기인 이름 끝의 '점'은 비교에서만 제거한다.
    private String removeBranchSuffix(String value) {
        return value.endsWith("점") ? value.substring(0, value.length() - 1) : value;
    }

    // 전체 주소가 같거나 상세 주소 충돌이 없는 기본 주소가 같을 때만 통과시킨다.
    private boolean matches(String kakaoAddress, String naverAddress) {
        String left = normalizer.normalizeAddress(kakaoAddress);
        String right = normalizer.normalizeAddress(naverAddress);
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        if (left.equals(right)) {
            return true;
        }
        return normalizer.normalizeBaseAddress(kakaoAddress).equals(normalizer.normalizeBaseAddress(naverAddress))
                && !normalizer.hasConflictingAddressDetail(kakaoAddress, naverAddress);
    }

    // 조건을 만족한 후보가 하나면 반환하고 여러 개면 자동 선택하지 않는다.
    private NaverLocalItem selectSingle(List<NaverLocalItem> matches, String condition) {
        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() == 1) {
            return matches.getFirst();
        }
        throw new PlaceRefinementException(
                PlaceRefinementErrorCode.NAVER_PLACE_AMBIGUOUS,
                condition + "이 같은 네이버 장소 후보가 여러 개입니다."
        );
    }
}
