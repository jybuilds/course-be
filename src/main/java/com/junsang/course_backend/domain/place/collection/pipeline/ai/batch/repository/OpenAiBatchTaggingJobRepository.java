package com.junsang.course_backend.domain.place.collection.pipeline.ai.batch.repository;

import com.junsang.course_backend.domain.place.collection.pipeline.ai.batch.entity.OpenAiBatchTaggingJob;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.batch.entity.OpenAiBatchTaggingStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpenAiBatchTaggingJobRepository extends JpaRepository<OpenAiBatchTaggingJob, Long> {

    List<OpenAiBatchTaggingJob> findByStatus(OpenAiBatchTaggingStatus status);
}
