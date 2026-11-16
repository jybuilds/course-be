package com.junsang.course_backend.domain.place.collection.scheduling;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import com.junsang.course_backend.domain.place.collection.scheduling.config.CollectionSchedulingProperties;
import com.junsang.course_backend.domain.place.collection.scheduling.policy.InitialCollectionPolicy;
import com.junsang.course_backend.domain.place.repository.AreaRepository;

class InitialCollectionPolicyTest {
    @Test void opensAtThreeKstAndCatchesUpAfterMissedStart() {
        var policy = new InitialCollectionPolicy(new CollectionSchedulingProperties(), mock(AreaRepository.class));
        assertThat(policy.canStartWork(ZonedDateTime.parse("2026-11-16T02:59:59+09:00"))).isFalse();
        assertThat(policy.canStartWork(ZonedDateTime.parse("2026-11-16T03:00:00+09:00"))).isTrue();
        assertThat(policy.canStartWork(ZonedDateTime.parse("2026-11-16T18:00:00+09:00"))).isTrue();
        assertThat(policy.canStartWork(ZonedDateTime.parse("2026-11-17T00:00:00+09:00"))).isFalse();
    }
}
