package com.junsang.course_backend.domain.place.collection.scheduling.config;

import java.time.LocalTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter @Setter @Validated
@ConfigurationProperties(prefix = "place-collection.scheduling")
public class CollectionSchedulingProperties {
    @NotBlank
    private String policy = "INIT";
    @NotBlank
    private String cityCode = "SEOUL";
    @NotNull
    private LocalTime startAt = LocalTime.of(3, 0);
    @NotBlank
    private String zone = "Asia/Seoul";
    @NotNull @Valid private Stage kakao = new Stage(60000, 100);
    @NotNull @Valid private Stage naver = new Stage(60000, 100);
    @NotNull @Valid private Stage aiSubmission = new Stage(60000, 1000);
    @NotNull @Valid private Stage aiResult = new Stage(60000, 5);
    @Min(1)
    private int maxNaverBacklog = 10000;
    @Min(1)
    private int maxAiBacklog = 5000;
    @Min(1)
    private int maxActiveBatches = 2;

    @Getter @Setter
    public static class Stage {
        @Min(1000)
        private long intervalMillis;
        @Min(1) @Max(1000)
        private int batchSize;
        public Stage(long intervalMillis, int batchSize) {
            this.intervalMillis = intervalMillis;
            this.batchSize = batchSize;
        }
    }
}
