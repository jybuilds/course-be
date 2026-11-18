package com.junsang.course_backend.infra.naver;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.junsang.course_backend.infra.naver.config.NaverSearchProperties;
import com.junsang.course_backend.domain.place.collection.budget.*;

class NaverCredentialRotationTest {
    @Test void retriesTheSameLocalSearchWithSecondKeyAndBlogUsesRemainingKey() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var writer = mock(CollectionBudgetWriter.class);
        var blocks = new HashMap<String, CollectionBudgetWriter.Block>();
        when(writer.find(any(), anyString())).thenAnswer(i -> Optional.ofNullable(blocks.get(i.getArgument(1))));
        doAnswer(i -> { blocks.put(i.getArgument(1), new CollectionBudgetWriter.Block(i.getArgument(2), i.getArgument(3))); return null; })
                .when(writer).block(any(), anyString(), any(), anyString());
        var budget = new CollectionBudgetService(writer, Clock.system(ZoneId.of("Asia/Seoul")), new ObjectMapper());
        var properties = new NaverSearchProperties(URI.create("https://test.invalid"), List.of(
                new ApiCredential("main", "id-a", "key-a"), new ApiCredential("team", "id-b", "key-b")));
        var local = new NaverLocalClient(builder, properties, new ObjectMapper(), budget);
        var blog = new NaverBlogClient(properties, builder, new ObjectMapper(), budget);
        server.expect(header("X-NCP-APIGW-API-KEY-ID", "id-a"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"errorCode\":429,\"message\":\"일별 사용량 한도가 초과하였습니다.\"}}"));
        server.expect(header("X-NCP-APIGW-API-KEY-ID", "id-b"))
                .andExpect(queryParam("query", "%EC%84%B1%EC%88%98%20%EC%B9%B4%ED%8E%98"))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));
        server.expect(header("X-NCP-APIGW-API-KEY-ID", "id-b"))
                .andExpect(queryParam("query", "%EC%84%B1%EC%88%98%20%EC%B9%B4%ED%8E%98"))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));
        assertThat(local.search("성수 카페").items()).isEmpty();
        assertThat(blog.search("성수 카페").items()).isEmpty();
        assertThat(blocks.get("main").reason()).isEqualTo("QUOTA_DAILY");
        server.verify();
    }

    @Test void dropsMalformedTeamCredentialBeforeSendingAnyRequestWithIt() {
        var main = new ApiCredential("main", "id-a", "key-a");
        var team = new ApiCredential("team", "팀원 아이디", "팀원 키");
        var baseUrl = URI.create("https://test.invalid");

        assertThat(new NaverSearchProperties(baseUrl, List.of(main, team)).credentials()).containsExactly(main);
        assertThat(new NaverSearchProperties(baseUrl, List.of(team)).isConfigured()).isFalse();
    }
}
