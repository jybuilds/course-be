package com.junsang.course_backend.infra.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.junsang.course_backend.infra.openai.config.OpenAiProperties;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiClientTest {

    @Test
    void returnsStructuredOutputText() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiClient client = new OpenAiClient(
                builder,
                new OpenAiProperties(
                        URI.create("https://api.openai.com"),
                        "api-key",
                        "gpt-5.4-nano"
                )
        );
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer api-key"))
                .andRespond(withSuccess("""
                        {
                          "output": [{
                            "type": "message",
                            "content": [{
                              "type": "output_text",
                              "text": "done"
                            }]
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        String output = client.createStructuredResponse(
                "태깅한다.",
                "{}",
                Map.of("type", "object")
        );

        assertThat(output).isEqualTo("done");
        server.verify();
    }
}
