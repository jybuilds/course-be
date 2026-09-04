package com.junsang.course_backend.domain.place.collection.pipeline.batch.kakao.entity;

public enum CollectionJobStatus {
    READY,
    RUNNING,
    COMPLETED,
    PARTIAL,
    SPLIT,
    FAILED
}
