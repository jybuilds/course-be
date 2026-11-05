package com.junsang.course_backend.recommendation.calculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.junsang.course_backend.domain.place.entity.Area;
import com.junsang.course_backend.domain.place.entity.AreaTag;
import com.junsang.course_backend.domain.place.entity.Tag;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AreaRecommendationCalculatorTest {
    private final AreaRecommendationCalculator calculator = new AreaRecommendationCalculator();

    @Test
    void classifiesBalancedBroadSpecialistAndFiller() {
        assertThat(calculator.evaluate(new double[]{90, 90, 90}).stage())
                .isEqualTo(AreaRecommendationCalculator.Stage.BALANCED);
        assertThat(calculator.evaluate(new double[]{100, 100, 100, 0}).stage())
                .isEqualTo(AreaRecommendationCalculator.Stage.BROAD);
        assertThat(calculator.evaluate(new double[]{100, 0, 0}).stage())
                .isEqualTo(AreaRecommendationCalculator.Stage.ALTERNATIVE);
        assertThat(calculator.evaluate(new double[]{60, 60, 60}).stage())
                .isEqualTo(AreaRecommendationCalculator.Stage.FILLER);
    }

    @Test
    void rewardsBalanceButRetainsExceptionalSpecialists() {
        assertThat(calculator.evaluate(new double[]{80, 80, 80}).value())
                .isGreaterThan(calculator.evaluate(new double[]{100, 100, 50}).value());
        assertThat(calculator.evaluate(new double[]{80, 80, 0}).value())
                .isGreaterThan(calculator.evaluate(new double[]{100, 0, 0}).value());
        assertThat(calculator.evaluate(new double[]{100, 0, 0}).value())
                .isGreaterThan(calculator.evaluate(new double[]{75, 0, 0}).value());
        assertThat(calculator.evaluate(new double[]{75, 75}).stage())
                .isEqualTo(AreaRecommendationCalculator.Stage.BALANCED);
        assertThat(calculator.evaluate(new double[]{100, 50}).stage())
                .isEqualTo(AreaRecommendationCalculator.Stage.BROAD);
    }

    @Test
    void usesFullPopulationPerTagAndMissingTagsContributeZero() {
        Area a = area(1L, null), b = area(2L, null), c = area(3L, null), d = area(4L, null);
        List<AreaTag> rows = List.of(row(a, "CAFE", 1), row(b, "CAFE", 2),
                row(c, "CAFE", 3), row(d, "CAFE", 4), row(a, "SHOPPING", 2), row(b, "SHOPPING", 1));
        // B=(66.7,100) is balanced; A=(100,0) is a specialist; C and D are fillers.
        assertThat(calculator.recommend(rows, List.of("CAFE", "SHOPPING"), 10))
                .containsExactly(2L, 1L, 3L, 4L);
        assertThat(calculator.recommend(rows, List.of("CAFE", "SHOPPING"), 1))
                .containsExactly(2L);
    }

    @Test
    void doesNotReserveSlotsForSpecialistsWhenThreeBalancedCandidatesExist() {
        List<AreaTag> rows = new ArrayList<>();
        for (int rank = 1; rank <= 9; rank++) {
            Area area = area((long) rank, null);
            rows.add(row(area, "CAFE", rank));
            rows.add(row(area, "SHOPPING", rank));
        }
        assertThat(calculator.recommend(rows, List.of("CAFE", "SHOPPING"), 3))
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void stagePriorityBeatsHigherRawAlternativeScore() {
        List<AreaTag> rows = new ArrayList<>();
        int[][] ranks = {{2, 2, 2}, {1, 1, 5}, {3, 3, 1}, {4, 4, 3}, {5, 5, 4}};
        for (int i = 0; i < ranks.length; i++) {
            Area area = area((long) i + 1, null);
            rows.add(row(area, "CAFE", ranks[i][0]));
            rows.add(row(area, "SHOPPING", ranks[i][1]));
            rows.add(row(area, "WALK", ranks[i][2]));
        }
        // Balanced A=(75,75,75) precedes alternative B=(100,100,0), whose RMS is 81.6.
        assertThat(calculator.recommend(rows, List.of("CAFE", "SHOPPING", "WALK"), 10))
                .containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void tiesUseOverallRankThenIdAndNullRankComesLast() {
        Area a = area(1L, null), b = area(2L, 1), c = area(3L, 1);
        List<AreaTag> rows = List.of(row(a, "CAFE", 1), row(b, "CAFE", 1), row(c, "CAFE", 1));
        assertThat(calculator.recommend(rows, List.of("CAFE"), 10)).containsExactly(2L, 3L, 1L);
    }

    @Test
    void handlesSingletonPopulationAndIgnoresUnrankedOrUnselectedRows() {
        Area a = area(1L, null), b = area(2L, null);
        List<AreaTag> rows = List.of(row(a, "CAFE", 1), row(b, "CAFE", null), row(b, "MARKET", 1));
        assertThat(calculator.recommend(rows, List.of("CAFE", "SHOPPING"), 10)).containsExactly(1L);
        assertThat(calculator.recommend(rows, List.of(), 10)).isEmpty();
        assertThat(calculator.recommend(List.of(), List.of("CAFE", "SHOPPING"), 10)).isEmpty();
        assertThat(calculator.recommend(rows, List.of("CAFE"), 0)).isEmpty();
    }

    @Test
    void improvingAnyTagCannotWorsenStageOrScoreWithinStage() {
        double[] levels = {0, 25, 49, 50, 60, 74, 75, 90, 100};
        for (double a : levels) for (double b : levels) for (double c : levels) {
            double[] values = {a, b, c};
            var before = calculator.evaluate(values);
            for (int i = 0; i < values.length; i++) {
                double[] improved = values.clone();
                improved[i] = Math.min(100, improved[i] + 1);
                var after = calculator.evaluate(improved);
                assertThat(after.stage().ordinal()).isLessThanOrEqualTo(before.stage().ordinal());
                if (after.stage() == before.stage()) {
                    assertThat(after.value() + 1e-9).isGreaterThanOrEqualTo(before.value());
                }
            }
        }
    }

    private Area area(Long id, Integer rank) {
        Area area = mock(Area.class);
        when(area.getId()).thenReturn(id);
        when(area.getRank()).thenReturn(rank);
        return area;
    }

    private AreaTag row(Area area, String code, Integer rank) {
        Tag tag = mock(Tag.class);
        when(tag.getCode()).thenReturn(code);
        AreaTag row = mock(AreaTag.class);
        when(row.getArea()).thenReturn(area);
        when(row.getTag()).thenReturn(tag);
        when(row.getRecommendationRank()).thenReturn(rank);
        return row;
    }
}
