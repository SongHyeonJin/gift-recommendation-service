package com.example.giftrecommender.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")
public class OpenAiConfig {
    @Bean
    @ConditionalOnProperty(prefix = "openai.api", name = "key")
    public OpenAIClient openAIClient(@Value("${openai.api.key}") String apiKey) {
        return OpenAIOkHttpClient.builder().apiKey(apiKey).build();
    }


}
