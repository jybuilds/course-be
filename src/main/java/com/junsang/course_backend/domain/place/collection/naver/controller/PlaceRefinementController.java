package com.junsang.course_backend.domain.place.collection.naver.controller;

import com.junsang.course_backend.domain.place.collection.naver.dto.PlaceRefinementBatchResponse;
import com.junsang.course_backend.domain.place.collection.naver.dto.PlaceRefinementResponse;
import com.junsang.course_backend.domain.place.collection.naver.service.PlaceRefinementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/// 네이버 장소 정제를 직접 실행하기 위한 내부 진입점이다.
@RestController
@RequestMapping("/internal/place-refinement")
@RequiredArgsConstructor
public class PlaceRefinementController {

    private final PlaceRefinementService placeRefinementService;

    // Temp 한 건을 네이버 지역 검색으로 정제한다.
    @PostMapping("/{tempId}")
    public ResponseEntity<PlaceRefinementResponse> refine(@PathVariable Long tempId) {
        return ResponseEntity.ok(placeRefinementService.refine(tempId));
    }

    // 네이버 정제 대기 데이터를 제한 개수만 순차 처리한다.
    @PostMapping("/naver")
    public ResponseEntity<PlaceRefinementBatchResponse> refinePending(
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ResponseEntity.ok(placeRefinementService.refinePending(limit));
    }
}
