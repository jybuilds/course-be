package com.junsang.course_backend.infra.openai.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
        URI baseUrl,
        String apiKey,
        String model,
        String organization,
        String project
) {

    // API Key와 모델이 모두 설정됐는지 확인한다.
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && model != null && !model.isBlank();
    }
}
