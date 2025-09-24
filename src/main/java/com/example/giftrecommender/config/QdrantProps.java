package com.example.giftrecommender.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qdrant")
@Getter @Setter
public class QdrantProps {
    private String httpBaseUrl;
    private String apiKey;
    private boolean tls;

    private final Grpc grpc = new Grpc();

    @Getter @Setter
    public static class Grpc {
        private String host;
        private int port;
    }
}
