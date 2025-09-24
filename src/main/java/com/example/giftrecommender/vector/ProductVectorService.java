package com.example.giftrecommender.vector;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "vector", name = "enabled", havingValue = "true")
public class ProductVectorService {

    private final QdrantClient qdrant;
    private final EmbeddingService embeddingService;

    private static List<Float> toFloatList(List<Float> src) {
        return new ArrayList<>(src);
    }

    public void upsertProduct(Long productId, String title, long price) throws Exception {
        List<Float> titleVec = embeddingService.embed(title);

        Points.PointStruct point = Points.PointStruct.newBuilder()
                .setId(Points.PointId.newBuilder().setNum(productId))
                .setVectors(
                        Points.Vectors.newBuilder()
                                .setVector(
                                        Points.Vector.newBuilder().addAllData(toFloatList(titleVec)).build()
                                )
                                .build()
                )
                .putPayload("productId", JsonWithInt.Value.newBuilder().setIntegerValue(productId).build())
                .putPayload("title", JsonWithInt.Value.newBuilder().setStringValue(title).build())
                .putPayload("price", JsonWithInt.Value.newBuilder().setIntegerValue(price).build())
                .build();

        Points.UpsertPoints upsert = Points.UpsertPoints.newBuilder()
                .setCollectionName("products")
                .addPoints(point)
                .build();

        qdrant.upsertAsync(upsert).get(10, TimeUnit.SECONDS);
    }
}
