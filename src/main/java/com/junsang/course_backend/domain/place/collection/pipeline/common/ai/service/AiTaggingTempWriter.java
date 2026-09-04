package com.junsang.course_backend.domain.place.collection.pipeline.common.ai.service;

import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionStep;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTempStatus;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// AI 태깅 전후 Temp 상태 변경을 트랜잭션으로 저장한다.
@Service
@RequiredArgsConstructor
public class AiTaggingTempWriter {

    private final PlaceCollectionTempRepository tempRepository;

    // 지정 Temp를 잠근 뒤 AI 태깅 대상으로 선점한다.
    @Transactional
    public Optional<PlaceCollectionTemp> start(Long tempId) {
        PlaceCollectionTemp temp = tempRepository.findByIdForUpdate(tempId)
                .orElseThrow(() -> new IllegalArgumentException("AI 태깅할 임시 장소를 찾을 수 없습니다: " + tempId));
        if (!isTaggable(temp)) {
            return Optional.empty();
        }
        temp.startAiTagging();
        tempRepository.save(temp);
        return Optional.of(temp);
    }

    // 대기 Temp를 잠근 뒤 즉시 태깅 대상으로 한 번에 선점한다.
    @Transactional
    public List<PlaceCollectionTemp> startPending(int limit) {
        List<PlaceCollectionTemp> targets = tempRepository.findByProcessingStepAndStatusOrderByIdAsc(
                PlaceCollectionStep.AI_TAGGING,
                PlaceCollectionTempStatus.PENDING,
                PageRequest.of(0, limit)
        );
        targets.forEach(PlaceCollectionTemp::startAiTagging);
        tempRepository.saveAll(targets);
        return targets;
    }

    private boolean isTaggable(PlaceCollectionTemp temp) {
        return temp.getProcessingStep() == PlaceCollectionStep.AI_TAGGING
                && (temp.getStatus() == PlaceCollectionTempStatus.PENDING
                || temp.getStatus() == PlaceCollectionTempStatus.FAILED);
    }
}
