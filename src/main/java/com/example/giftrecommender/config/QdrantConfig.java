package com.example.giftrecommender.config;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(QdrantProps.class)
@ConditionalOnProperty(prefix = "vector", name = "enabled", havingValue = "true")
public class QdrantConfig {

    @Bean
    public WebClient qdrantWebClient(QdrantProps p) {
        return WebClient.builder()
                .baseUrl(p.getHttpBaseUrl())
                .build();
    }

    @Bean(destroyMethod = "shutdownNow")
    public ManagedChannel qdrantChannel(QdrantProps p) {
        ManagedChannelBuilder<?> builder =
                ManagedChannelBuilder.forAddress(p.getGrpc().getHost(), p.getGrpc().getPort());
        if (p.isTls()) builder.useTransportSecurity();
        else builder.usePlaintext();
        return builder.build();
    }

    @Bean
    public QdrantClient qdrantClient(QdrantProps p) {
        QdrantGrpcClient grpc = QdrantGrpcClient.newBuilder(
                p.getGrpc().getHost(),
                p.getGrpc().getPort(),
                p.isTls()
        ).build();
        return new QdrantClient(grpc);
    }
}
