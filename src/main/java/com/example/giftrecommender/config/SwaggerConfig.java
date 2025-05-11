package com.example.giftrecommender.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {

        Info info = new Info()
                .title("Gift Recommender API")
                .description("선물 추천 API 명세서입니다.")
                .version("v1.0.0");

        return new OpenAPI().info(info);
    }

}
