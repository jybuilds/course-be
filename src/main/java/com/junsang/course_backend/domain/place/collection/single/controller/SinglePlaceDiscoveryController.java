package com.junsang.course_backend.domain.place.collection.single.controller;

import com.junsang.course_backend.domain.place.collection.single.dto.SingleAnchorRequest;
import com.junsang.course_backend.domain.place.collection.single.dto.SingleAnchorResponse;
import com.junsang.course_backend.domain.place.collection.single.dto.SinglePlaceCandidateResponse;
import com.junsang.course_backend.domain.place.collection.single.service.SinglePlaceDiscoveryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/// 직접 코스 생성에서 장소를 찾고 DB에 없는 앵커를 확정하는 API다.
@RestController
@Validated
@RequestMapping("/api/place-discovery")
@RequiredArgsConstructor
public class SinglePlaceDiscoveryController {

    private final SinglePlaceDiscoveryService onDemandPlaceDiscoveryService;

    // 내부 DB 우선으로 후보를 찾고, 없을 때만 카카오 후보를 반환한다.
    @GetMapping
    public ResponseEntity<List<SinglePlaceCandidateResponse>> search(
            @RequestParam @NotBlank String query,
            @RequestParam @DecimalMin("33.0") @DecimalMax("39.0") BigDecimal latitude,
            @RequestParam @DecimalMin("124.0") @DecimalMax("132.0") BigDecimal longitude
    ) {
        return ResponseEntity.ok(onDemandPlaceDiscoveryService.search(query, latitude, longitude));
    }

    // 사용자가 선택한 외부 후보를 즉시 앵커 Place로 만들고 후속 정제를 등록한다.
    @PostMapping("/anchors")
    public ResponseEntity<SingleAnchorResponse> selectAnchor(
            @Valid @RequestBody SingleAnchorRequest request
    ) {
        return ResponseEntity.ok(onDemandPlaceDiscoveryService.selectAnchor(request));
    }
}
