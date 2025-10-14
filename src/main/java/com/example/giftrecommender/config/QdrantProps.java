package com.example.giftrecommender.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qdrant")
@Getter @Setter
public class QdrantProps {
    private String httpBaseUrl;
    private boolean tls;
    private String collection;
    private double defaultScoreThreshold = 0.75;

    private final Grpc grpc = new Grpc();

    @Getter @Setter
    public static class Grpc {
        private String host;
        private int port;
    }

    @Getter @Setter
    public static class Search {
        private double threshold = 0.75;
        private int limit = 10;
    }
}
