package com.example.giftrecommender.infra.coupang.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class CoupangApiConfig {
    @Value("${coupang.access-key}")
    private String accessKey;
    @Value("${coupang.secret-key}")
    private String secretKey;
}
