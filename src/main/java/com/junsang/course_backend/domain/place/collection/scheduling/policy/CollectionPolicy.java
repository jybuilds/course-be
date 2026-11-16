package com.junsang.course_backend.domain.place.collection.scheduling.policy;

import java.time.ZonedDateTime;
import java.util.List;
import com.junsang.course_backend.domain.place.entity.Area;
import com.junsang.course_backend.domain.place.collection.scheduling.config.CollectionSchedulingProperties;

/** 작업 선정과 네 단계 실행 기준. 오류 복구와 저장은 처리 서비스가 담당한다. */
public interface CollectionPolicy {
    String name();

    CollectionSchedulingProperties settings();

    boolean canStartWork(ZonedDateTime now);

    List<Area> nextAreas();
}
