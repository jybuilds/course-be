package com.junsang.course_backend.domain.place.collection.pipeline.orchestration;

import com.junsang.course_backend.domain.place.collection.pipeline.ai.service.OpenAiBatchTaggingService;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.service.KakaoAreaCollectionService;
import com.junsang.course_backend.domain.place.collection.pipeline.naver.dto.PlaceRefinementBatchResponse;
import com.junsang.course_backend.domain.place.collection.pipeline.naver.service.PlaceRefinementService;
import com.junsang.course_backend.domain.place.collection.pipeline.orchestration.dto.AreaCollectionPipelineResponse;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlaceCollectionOrchestrator {

    private final KakaoAreaCollectionService kakaoAreaCollectionService;
    private final PlaceRefinementService placeRefinementService;
    private final OpenAiBatchTaggingService openAiBatchTaggingService;
    private final PlaceCollectionTempRepository tempRepository;

    public AreaCollectionPipelineResponse runArea(Long areaId, int refinementLimit, int taggingLimit) {
        kakaoAreaCollectionService.collectArea(areaId);
        PlaceRefinementBatchResponse refinement = placeRefinementService.refinePendingByArea(areaId, refinementLimit);
        Long aiBatchJobId = openAiBatchTaggingService.submitPendingByArea(areaId, taggingLimit);

        return new AreaCollectionPipelineResponse(
                areaId,
                tempRepository.countByAreaId(areaId),
                refinement,
                aiBatchJobId
        );
    }
}
