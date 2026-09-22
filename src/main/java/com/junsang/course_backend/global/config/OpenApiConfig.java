package com.junsang.course_backend.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Paths;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@OpenAPIDefinition(tags = {
        @Tag(
                name = "1. 옵션 선택",
                description = "사용자 선택지를 조회하고, 선택 결과를 바탕으로 추천 지역을 정하는 단계입니다."
        ),
        @Tag(
                name = "2. 앵커 목록 추천",
                description = "선택한 Area 안에서 사용자 선택지와 인기도를 반영해 앵커 장소를 추천하는 단계입니다."
        ),
        @Tag(
                name = "3. 코스 추천",
                description = "선택한 앵커를 기준으로 식당·카페 후보를 즉시 조합해 반환하는 단계입니다."
        ),
        @Tag(
                name = "4. 사용자 커스텀",
                description = "사용자가 기준 장소와 요청 카테고리를 지정해 코스의 장소를 바꾸는 단계입니다."
        ),
        @Tag(
                name = "5. 코스 저장",
                description = "사용자가 완성한 코스를 저장하고 서버에서 제목을 생성하는 단계입니다."
        ),
        @Tag(
                name = "코스 추천 API - 미사용",
                description = "대표 코스 추천 API입니다. 신규 Area·앵커 추천 구현 전까지 호환성을 위해 유지합니다."
        ),
        @Tag(
                name = "장소 수집 및 정제 - 미사용",
                description = "장소 데이터를 수집·정제·태깅하는 내부 운영용 API입니다. 서비스 사용자 호출 대상이 아닙니다."
        ),
        @Tag(
                name = "장소 확인 - 개발용",
                description = "수집·태깅 완료된 장소 데이터를 사람이 검토하는 개발용 API입니다."
        )
})
/// API 문서의 서비스 이름과 버전을 설정한다.
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI courseOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Course API")
                .version("v1")
                .description("Area와 앵커를 추천하고 사용자가 코스를 완성하는 API입니다."));
    }

    // 장소 수집 파이프라인의 Swagger 노출 순서를 실제 실행 순서에 맞춘다.
    @Bean
    public OpenApiCustomizer placeCollectionOperationOrderCustomizer() {
        return openApi -> {
            Paths paths = openApi.getPaths();
            Paths orderedPaths = new Paths();

            List.of(
                    "/internal/place-collection/batch/cities/{cityId}",
                    "/internal/place-collection/batch/areas/{areaId}",
                    "/internal/place-refinement/naver",
                    "/internal/place-collection/batch/ai",
                    "/internal/place-collection/batch/ai/{jobId}/collect",
                    "/internal/place-collection/batch/ai/re-tag",
                    "/internal/place-collection/test/areas/{areaId}",
                    "/internal/place-refinement/ai",
                    "/internal/place-refinement/ai/{tempId}"
            ).forEach(path -> {
                if (paths.containsKey(path)) {
                    orderedPaths.addPathItem(path, paths.remove(path));
                }
            });

            paths.forEach(orderedPaths::addPathItem);
            openApi.setPaths(orderedPaths);
        };
    }
}
