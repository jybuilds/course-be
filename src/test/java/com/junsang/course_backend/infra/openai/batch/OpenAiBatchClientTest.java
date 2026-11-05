package com.junsang.course_backend.infra.openai.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.junsang.course_backend.infra.openai.config.AiProviderProperties;
import java.net.URI;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiBatchClientTest {
    @ParameterizedTest
    @ValueSource(strings = {"OPENAI", "OMNIROUTE", "OMNIROUTE_ERROR"})
    void submitsAndCollectsThroughSelectedProvider(String provider) {
        RestClient.Builder builder = spy(RestClient.builder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // 실제 전송 설정 대신 Mock 서버를 유지한다. 실제 전송은 별도 LiveTest로 검증한다.
        doReturn(builder).when(builder).clone();
        doReturn(builder).when(builder).requestFactory(any());
        var settings = new AiProviderProperties.ProviderSettings(
                URI.create("http://provider.test"), "test-key", "test-model", null, null);
        var properties = new AiProviderProperties(provider.replace("_ERROR", ""),
                "OPENAI".equals(provider) ? settings : null,
                provider.startsWith("OMNIROUTE") ? settings : null);
        OpenAiBatchClient client = new OpenAiBatchClient(properties, builder);

        server.expect(requestTo("http://provider.test/v1/files"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andRespond(withSuccess("{\"id\":\"file-input\"}", MediaType.APPLICATION_JSON));
        if ("OPENAI".equals(provider)) {
            server.expect(requestTo("http://provider.test/v1/files/file-input"))
                    .andRespond(withSuccess("{\"status\":\"processed\"}", MediaType.APPLICATION_JSON));
        }
        server.expect(requestTo("http://provider.test/v1/batches"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.input_file_id").value("file-input"))
                .andRespond(withSuccess("{\"id\":\"batch-test\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://provider.test/v1/batches/batch-test"))
                .andRespond(withSuccess(provider.endsWith("_ERROR")
                        ? "{\"status\":\"completed\",\"output_file_id\":null,\"error_file_id\":\"file-output\"}"
                        : "{\"status\":\"completed\",\"output_file_id\":\"file-output\",\"error_file_id\":null}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://provider.test/v1/files/file-output/content"))
                .andRespond(withSuccess("result-jsonl", MediaType.TEXT_PLAIN));

        assertThat(client.submit("request-jsonl")).isEqualTo("batch-test");
        assertThat(client.completedOutput("batch-test")).isEqualTo("result-jsonl");
        server.verify();
    }
}
