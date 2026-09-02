package com.junsang.course_backend.infra.naver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.junsang.course_backend.infra.naver.config.NaverSearchProperties;
import com.junsang.course_backend.infra.naver.dto.response.NaverBlogSearchResponse;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NaverBlogClientTest {

    @Test
    void searchesBlogPostsWithNaverCredentials() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NaverBlogClient client = new NaverBlogClient(
                new NaverSearchProperties(URI.create("https://naverapihub.apigw.ntruss.com"), "client-id", "client-secret"),
                builder,
                new ObjectMapper()
        );
        server.expect(requestTo("https://naverapihub.apigw.ntruss.com/search/v1/blog?query=%EC%84%B1%EC%8B%AC%EB%8B%B9%20%EB%8C%80%EC%A0%84&display=20&start=1&sort=sim&format=json"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("display", "20"))
                .andExpect(header("X-NCP-APIGW-API-KEY-ID", "client-id"))
                .andExpect(header("X-NCP-APIGW-API-KEY", "client-secret"))
                .andRespond(withSuccess("""
                        {
                          "total": 1,
                          "start": 1,
                          "display": 1,
                          "items": [{
                            "title": "<b>성심당</b> 본점 후기",
                            "description": "대전 빵집 방문 후기",
                            "postdate": "20260831"
                          }]
                        }
                        """, MediaType.TEXT_PLAIN));

        NaverBlogSearchResponse response = client.search("성심당 대전");

        assertThat(response.items()).singleElement()
                .satisfies(item -> assertThat(item.postdate()).isEqualTo("20260831"));
        server.verify();
    }
}
