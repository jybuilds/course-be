package com.junsang.course_backend.domain.place.service;

import com.junsang.course_backend.domain.place.entity.Place;
import com.junsang.course_backend.domain.place.repository.PlaceRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.common.naver.service.NaverSearchUrlCreator;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 기존 Place의 네이버 검색 링크만 현재 생성 규칙으로 다시 계산한다.
@Service
@RequiredArgsConstructor
public class PlaceNaverLinkRefreshService {

    private static final int PAGE_SIZE = 500;

    private final PlaceRepository placeRepository;
    private final NaverSearchUrlCreator naverSearchUrlCreator;
    private final EntityManager entityManager;

    @Transactional
    public RefreshResult refreshAll() {
        Long lastId = 0L;
        int updated = 0;
        int unchanged = 0;

        while (true) {
            List<Place> places = placeRepository.findAllByIdGreaterThanOrderByIdAsc(
                    lastId,
                    PageRequest.of(0, PAGE_SIZE)
            );
            if (places.isEmpty()) {
                break;
            }

            for (Place place : places) {
                String newUrl = naverSearchUrlCreator.create(place.getName(), place.getAddressName());
                if (newUrl.equals(place.getNaverSearchUrl())) {
                    unchanged++;
                } else {
                    place.updateNaverSearchUrl(newUrl);
                    updated++;
                }
            }

            lastId = places.get(places.size() - 1).getId();
            entityManager.flush();
            entityManager.clear();
        }

        return new RefreshResult(updated, unchanged);
    }

    public record RefreshResult(int updated, int unchanged) {
    }
}
