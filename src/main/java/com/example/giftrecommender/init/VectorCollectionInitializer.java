package com.example.giftrecommender.init;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "vector", name = "enabled", havingValue = "true")
public class VectorCollectionInitializer implements ApplicationRunner {

    private final QdrantClient qdrant;
    private static final String COLLECTION = "products";
    private static final int DIM = 1536; // text-embedding-3-small

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 컬렉션 생성 시도, 이미 있으면 ALREADY_EXISTS 처리
        Collections.VectorParams vectorParams = Collections.VectorParams.newBuilder()
                .setSize(DIM)
                .setDistance(Collections.Distance.Cosine)
                .build();

        Collections.VectorsConfig vectorsConfig = Collections.VectorsConfig.newBuilder()
                .setParams(vectorParams)
                .build();

        Collections.CreateCollection createReq = Collections.CreateCollection.newBuilder()
                .setCollectionName(COLLECTION)
                .setVectorsConfig(vectorsConfig)
                .build();

        try {
            qdrant.createCollectionAsync(createReq).get(20, TimeUnit.SECONDS);
            log.info("Qdrant collection '" + COLLECTION + "' created");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof StatusRuntimeException) {
                Status.Code code = ((StatusRuntimeException) cause).getStatus().getCode();
                if (code == Status.ALREADY_EXISTS.getCode()) {
                    log.info("Collection already exists. Skip create.");
                    return;
                }
            }
            throw ex;
        }
    }
}
