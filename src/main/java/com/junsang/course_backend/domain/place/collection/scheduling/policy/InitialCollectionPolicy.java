package com.junsang.course_backend.domain.place.collection.scheduling.policy;

import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import com.junsang.course_backend.domain.place.entity.Area;
import com.junsang.course_backend.domain.place.repository.AreaRepository;
import com.junsang.course_backend.domain.place.collection.scheduling.config.CollectionSchedulingProperties;

@Component @RequiredArgsConstructor
public class InitialCollectionPolicy implements CollectionPolicy {
    private final CollectionSchedulingProperties settings;
    private final AreaRepository areas;

    public String name() {
        return "INIT";
    }

    public CollectionSchedulingProperties settings() {
        return settings;
    }

    public boolean canStartWork(ZonedDateTime now) {
        return !now.toLocalTime().isBefore(settings.getStartAt());
    }

    public List<Area> nextAreas() {
        return areas.findUnregisteredCollectionAreas(settings.getCityCode(), PageRequest.of(0, 1));
    }
}
