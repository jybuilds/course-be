package com.junsang.course_backend.global.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Configuration
public class ExternalApiClientConfig {
    @Bean
    public RestClientCustomizer externalApiTimeouts() {
        return builder -> {
            var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10)).build());
            factory.setReadTimeout(Duration.ofSeconds(30));
            builder.requestFactory(factory);
        };
    }
}
