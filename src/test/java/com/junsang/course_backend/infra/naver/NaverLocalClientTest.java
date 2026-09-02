package com.junsang.course_backend.infra.naver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.junsang.course_backend.infra.naver.config.NaverSearchProperties;
import com.junsang.course_backend.infra.naver.dto.response.NaverLocalSearchResponse;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NaverLocalClientTest {

    @Test
    void searchesLocalPlacesWithNaverCredentials() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NaverLocalClient client = new NaverLocalClient(
                builder,
                new NaverSearchProperties(URI.create("https://naverapihub.apigw.ntruss.com"), "client-id", "client-secret"),
                new ObjectMapper()
        );
        server.expect(requestTo("https://naverapihub.apigw.ntruss.com/search/v1/local?query=%EC%84%B1%EC%8B%AC%EB%8B%B9%20%EB%8C%80%EC%A0%84&display=5&start=1&sort=random&format=json"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("display", "5"))
                .andExpect(header("X-NCP-APIGW-API-KEY-ID", "client-id"))
                .andExpect(header("X-NCP-APIGW-API-KEY", "client-secret"))
                .andRespond(withSuccess("""
                        {
                          "total": 1,
                          "start": 1,
                          "display": 1,
                          "items": [{
                            "title": "<b>성심당</b> 본점",
                            "link": "https://example.com/place",
                            "category": "카페,디저트>베이커리",
                            "description": "",
                            "telephone": "",
                            "address": "대전광역시 중구 은행동 145",
                            "roadAddress": "대전광역시 중구 대종로480번길 15",
                            "mapx": "1274277000",
                            "mapy": "363275000"
                          }]
                        }
                        """, MediaType.TEXT_PLAIN));

        NaverLocalSearchResponse response = client.search("성심당 대전");

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().link()).isEqualTo("https://example.com/place");
        server.verify();
    }
}
