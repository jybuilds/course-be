package com.junsang.course_backend.infra.openai.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.pipeline.batch.ai.service.OpenAiBatchTaggingService;
import com.junsang.course_backend.domain.place.collection.pipeline.common.ai.service.AiTaggingInputBuilder;
import com.junsang.course_backend.domain.place.entity.Tag;
import com.junsang.course_backend.infra.openai.config.AiProviderProperties;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

// 명시적으로 활성화한 경우에만 실제 공급자에 소량 요청한다. DB는 변경하지 않는다.
@EnabledIfEnvironmentVariable(named = "OMNIROUTE_LIVE_TEST", matches = "true")
class OmniRouteBatchLiveTest {
    @Test
    void submitsProductionRequestAndCollectsStructuredResult() throws Exception {
        var settings = new AiProviderProperties.ProviderSettings(
                URI.create(System.getenv("OMNIROUTE_BASE_URL")), System.getenv("OMNIROUTE_API_KEY"),
                System.getenv("OMNIROUTE_MODEL"), null, null);
        var properties = new AiProviderProperties("OMNIROUTE", null, settings);
        var client = new OpenAiBatchClient(properties, RestClient.builder());
        var mapper = new ObjectMapper();
        var builder = mock(AiTaggingInputBuilder.class);
        var temp = mock(PlaceCollectionTemp.class);
        when(temp.getId()).thenReturn(1L);
        var tag = mock(Tag.class);
        when(tag.getCode()).thenReturn("CAFE");
        var targets = List.of(temp);
        var tags = List.of(tag);
        when(builder.create(targets, tags, List.of())).thenReturn(Map.of(
                "availableTags", List.of(Map.of("code", "CAFE", "displayName", "카페")),
                "typeRules", List.of(),
                "places", List.of(Map.of("tempId", 1, "placeType", "CAFE", "evidence", Map.of(
                        "name", "스타벅스 합정메세나몰점", "naverCategory", "카페,디저트>카페")))));
        var service = new OpenAiBatchTaggingService(null, null, client, properties, mapper, null, null, builder);
        String jsonl = ReflectionTestUtils.invokeMethod(service, "createJsonLines", targets, tags, List.of());
        String batchId = client.submit(jsonl);
        System.out.println("Live batch ID: " + batchId);
        String output = null;
        for (int i = 0; i < 60 && output == null; i++) {
            Thread.sleep(1000);
            output = client.completedOutput(batchId);
        }
        assertThat(output).isNotBlank();
        var body = mapper.readTree(output.lines().findFirst().orElseThrow()).path("response").path("body");
        String resultText = body.path("output").get(0).path("content").get(0).path("text").asText();
        var result = mapper.readTree(resultText).path("results").get(0);
        assertThat(result.path("tempId").asLong()).isEqualTo(1);
        assertThat(result.path("placeType").asText()).isEqualTo("CAFE");
        assertThat(result.path("tags").size()).isPositive();
    }
}
