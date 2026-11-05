package com.junsang.course_backend.infra.openai.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public record AiProviderProperties(
        String provider,
        ProviderSettings openai,
        ProviderSettings omniroute
) {

    public ProviderSettings selected() {
        if ("OPENAI".equalsIgnoreCase(provider)) {
            return openai;
        }
        if ("OMNIROUTE".equalsIgnoreCase(provider)) {
            return omniroute;
        }
        throw new IllegalStateException("지원하지 않는 AI_PROVIDER입니다: " + provider);
    }

    public boolean isConfigured() {
        ProviderSettings active = selected();
        return active != null
                && active.baseUrl() != null
                && active.apiKey() != null && !active.apiKey().isBlank()
                && active.model() != null && !active.model().isBlank();
    }

    public record ProviderSettings(
            URI baseUrl,
            String apiKey,
            String model,
            String organization,
            String project
    ) {
    }
}
