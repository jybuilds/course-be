package com.junsang.course_backend.domain.place.collection.pipeline.batch.ai.repository;

import com.junsang.course_backend.domain.place.collection.pipeline.batch.ai.entity.OpenAiBatchTaggingJob;
import com.junsang.course_backend.domain.place.collection.pipeline.batch.ai.entity.OpenAiBatchTaggingStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpenAiBatchTaggingJobRepository extends JpaRepository<OpenAiBatchTaggingJob, Long> {

    List<OpenAiBatchTaggingJob> findByStatus(OpenAiBatchTaggingStatus status);
}
