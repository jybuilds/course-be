package com.junsang.course_backend.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.junsang.course_backend.domain.place.entity.Area;
import com.junsang.course_backend.domain.place.entity.AreaTag;
import com.junsang.course_backend.domain.place.entity.Tag;
import com.junsang.course_backend.domain.place.repository.AreaRepository;
import com.junsang.course_backend.domain.place.repository.AreaTagRepository;
import com.junsang.course_backend.domain.place.repository.CityRepository;
import com.junsang.course_backend.domain.place.repository.TagRepository;
import com.junsang.course_backend.recommendation.calculator.AreaRecommendationCalculator;
import com.junsang.course_backend.recommendation.dto.request.RecommendationRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationServiceTest {
    private final AreaTagRepository areaTags = mock(AreaTagRepository.class);
    private final AreaRepository areas = mock(AreaRepository.class);
    private final RecommendationService service = new RecommendationService(
            mock(CityRepository.class), mock(TagRepository.class), areaTags, areas,
            new AreaRecommendationCalculator());

    @Test
    void multiTagLoadsOnceAndGroupsSelectedTagsInCalculatorOrder() {
        Area a = area(1L), b = area(2L);
        List<AreaTag> rows = List.of(row(b, "CAFE", 2), row(a, "CAFE", 1),
                row(b, "SHOPPING", 2), row(a, "SHOPPING", 1));
        when(areaTags.findRankedByCityAndTagCodes(1L, List.of("CAFE", "SHOPPING")))
                .thenReturn(rows);
        when(areas.findByCity_IdAndRankIsNotNullOrderByRankAscIdAsc(eq(1L), any()))
                .thenReturn(List.of());
        var result = service.recommendationAreas(request(List.of("CAFE", "SHOPPING", "GAME")));
        assertThat(result.areas()).extracting(r -> r.areaId()).containsExactly(1L, 2L);
        assertThat(result.areas().getFirst().areaTags()).hasSize(2);
        assertThat(result.areas().getFirst().areaTags().getFirst().recommendedVisitPlaces())
                .containsExactly("place");
        verify(areaTags, times(1)).findRankedByCityAndTagCodes(1L, List.of("CAFE", "SHOPPING"));
        verify(areaTags, never()).findAllByAreaIds(any());
    }

    @Test
    void supplementsAfterAllTagCandidatesWithoutDuplicatingAreas() {
        Area candidate = area(1L), fallback = area(9L);
        List<AreaTag> rows = List.of(row(candidate, "CAFE", 1));
        when(areaTags.findRankedByCityAndTagCodes(1L, List.of("CAFE", "SHOPPING")))
                .thenReturn(rows);
        when(areas.findByCity_IdAndRankIsNotNullOrderByRankAscIdAsc(eq(1L), any()))
                .thenReturn(List.of(candidate, fallback));
        when(areaTags.findAllForAreasAndTags(List.of(9L), List.of("CAFE", "SHOPPING")))
                .thenReturn(List.of());
        var result = service.recommendationAreas(request(List.of("CAFE", "SHOPPING")));
        assertThat(result.areas()).extracting(r -> r.areaId()).containsExactly(1L, 9L);
        assertThat(result.areas().getLast().areaTags()).isEmpty();
        verify(areaTags).findAllForAreasAndTags(List.of(9L), List.of("CAFE", "SHOPPING"));
    }

    @Test
    void popularRecommendationGroupsAllTagsAndRetainsAreaWithoutTags() {
        Area a = area(1L), b = area(2L);
        when(areas.findByCity_IdAndRankIsNotNullOrderByRankAscIdAsc(eq(1L), any()))
                .thenReturn(List.of(a, b));
        List<AreaTag> rows = List.of(row(a, "CAFE", 1), row(a, "SHOPPING", 1));
        when(areaTags.findAllByAreaIds(List.of(1L, 2L))).thenReturn(rows);
        var result = service.recommendationAreas(request(List.of()));
        assertThat(result.areas()).extracting(r -> r.areaId()).containsExactly(1L, 2L);
        assertThat(result.areas().getFirst().areaTags()).hasSize(2);
        assertThat(result.areas().getLast().areaTags()).isEmpty();
        verify(areaTags, never()).findRankedByCityAndTagCodes(any(), any());
    }

    @Test
    void singleTagKeepsTagRankOrder() {
        Area a = area(1L), b = area(2L);
        List<AreaTag> rows = List.of(row(b, "CAFE", 1), row(a, "CAFE", 2));
        when(areaTags.findRankedAreaTagsByTagCode(eq("CAFE"), eq(1L), any()))
                .thenReturn(rows);
        var result = service.recommendationAreas(request(List.of("CAFE")));
        assertThat(result.areas()).extracting(r -> r.areaId()).containsExactly(2L, 1L);
        verifyNoInteractions(areas);
    }

    private RecommendationRequest request(List<String> tags) {
        return new RecommendationRequest(1L, null, null, tags);
    }

    private Area area(Long id) {
        Area area = mock(Area.class);
        when(area.getId()).thenReturn(id);
        when(area.getName()).thenReturn("Area " + id);
        return area;
    }

    private AreaTag row(Area area, String code, int rank) {
        Tag tag = mock(Tag.class);
        when(tag.getCode()).thenReturn(code);
        when(tag.getDisplayName()).thenReturn(code);
        AreaTag row = mock(AreaTag.class);
        when(row.getArea()).thenReturn(area);
        when(row.getTag()).thenReturn(tag);
        when(row.getRecommendationRank()).thenReturn(rank);
        when(row.getRecommendVisitPlaces()).thenReturn(List.of("place"));
        return row;
    }
}
