package com.junsang.course_backend.domain.place.collection.pipeline.ai.service;

import com.junsang.course_backend.domain.place.collection.pipeline.ai.dto.AiTaggingResultBatch.AiTaggingResult;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.service.AiTaggingWriter;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.PlaceCategoryRule;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import com.junsang.course_backend.domain.place.entity.Tag;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 장소 저장과 반영 완료 기록을 같은 트랜잭션에 넣어 재수집을 안전하게 한다. */
@Service
@RequiredArgsConstructor
public class AiBatchResultWriter {
    private final JdbcTemplate jdbc;
    private final PlaceCollectionTempRepository temps;
    private final AiTaggingWriter writer;

    @Transactional
    public void complete(Long jobId, String token, AiTaggingResult result, Map<String, Tag> tags, List<PlaceCategoryRule> rules) {
        String owner = jdbc.queryForObject("SELECT collect_token FROM openai_batch_tagging_jobs WHERE id=? FOR UPDATE", String.class, jobId);
        if (!token.equals(owner)) throw new OpenAiBatchJobWriter.CollectionLeaseLostException();
        String state = jdbc.queryForObject("SELECT status FROM ai_batch_tagging_items WHERE job_id=? AND temp_id=? FOR UPDATE",
                String.class, jobId, result.tempId());
        if (!"PENDING".equals(state)) return;
        var temp = temps.findByIdForUpdate(result.tempId()).orElseThrow();
        if (!jobId.equals(temp.getAiBatchJobId())) throw new IllegalStateException("Temp의 현재 Batch가 변경됨");
        writer.complete(result.tempId(), result, tags, rules);
        jdbc.update("UPDATE ai_batch_tagging_items SET status='COMPLETED', updated_at=CURRENT_TIMESTAMP WHERE job_id=? AND temp_id=?",
                jobId, result.tempId());
    }
}
