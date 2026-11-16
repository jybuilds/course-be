package com.junsang.course_backend.domain.place.collection.scheduling;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.junsang.course_backend.domain.place.collection.budget.CollectionDeferredException;
import com.junsang.course_backend.domain.place.collection.entity.*;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.naver.service.*;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.service.*;
import com.junsang.course_backend.domain.place.entity.*;
import com.junsang.course_backend.infra.naver.*;

class CollectionDeferralTest {
    @Test void naverBudgetWaitReturnsTempToPendingAndBypassesFailureHandler() {
        var temps = mock(PlaceCollectionTempRepository.class);
        var local = mock(NaverLocalClient.class);
        var writer = mock(NaverRefinementWriter.class);
        var error = mock(NaverRefinementErrorHandler.class);
        var classifier = mock(PlaceTypeClassifier.class);
        var creator = mock(NaverSearchUrlCreator.class);
        var temp = PlaceCollectionTemp.create(mock(Area.class), PlaceProvider.KAKAO, "test", PlaceType.CAFE,
                "test", "서울 성수동", "", "카페", "CE7", BigDecimal.ZERO, BigDecimal.ZERO, "", "");
        when(writer.find(1L)).thenReturn(temp);
        when(writer.tryStart(1L)).thenReturn(true);
        when(classifier.findActiveRules()).thenReturn(List.of());
        when(creator.createQuery(anyString(), anyString())).thenReturn("query");
        Instant resume = Instant.parse("2026-12-01T00:00:00Z");
        when(local.search(anyString())).thenThrow(new CollectionDeferredException(resume));
        var service = new PlaceRefinementService(temps, local, mock(NaverBlogClient.class), mock(NaverPlaceMatcher.class),
                mock(NaverPlaceNormalizer.class), creator, mock(NaverBlogEvidenceFilter.class), writer, classifier, error);
        assertThatThrownBy(() -> service.refine(1L)).isInstanceOf(CollectionDeferredException.class);
        verify(writer).defer(1L, LocalDateTime.ofInstant(resume, ZoneId.systemDefault()));
        verifyNoInteractions(error);
    }
}
