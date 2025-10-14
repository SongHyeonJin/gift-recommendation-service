package com.example.giftrecommender.vector;

import com.example.giftrecommender.config.QdrantProps;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "vector", name = "enabled", havingValue = "true")
public class ProductVectorService {

    private final QdrantClient qdrant;
    private final EmbeddingService embeddingService;
    private final QdrantProps qdrantProps;

    private static List<Float> toFloatList(List<Float> src) {
        return new ArrayList<>(src);
    }

    /** 문자열 리스트 → JsonWithInt.ListValue 변환 (payload 배열용) */
    private static JsonWithInt.Value toStringArrayValue(List<String> items) {
        JsonWithInt.ListValue.Builder list = JsonWithInt.ListValue.newBuilder();
        if (items != null) {
            for (String s : items) {
                if (s == null) continue;
                String v = s.trim();
                if (v.isEmpty()) continue;
                list.addValues(JsonWithInt.Value.newBuilder().setStringValue(v).build());
            }
        }
        return JsonWithInt.Value.newBuilder().setListValue(list.build()).build();
    }

    /**
     * 상품 업서트: 벡터 + payload(title, price, productId, keywords[])
     */
    public void upsertProduct(Long productId, String title, long price, List<String> keywords) throws Exception {
        List<Float> titleVec = embeddingService.embed(title);

        Points.PointStruct.Builder point = Points.PointStruct.newBuilder()
                .setId(Points.PointId.newBuilder().setNum(productId))
                .setVectors(
                        Points.Vectors.newBuilder()
                                .setVector(
                                        Points.Vector.newBuilder()
                                                .addAllData(toFloatList(titleVec))
                                                .build()
                                )
                                .build()
                )
                .putPayload("productId", JsonWithInt.Value.newBuilder().setIntegerValue(productId).build())
                .putPayload("title", JsonWithInt.Value.newBuilder().setStringValue(title).build())
                .putPayload("price", JsonWithInt.Value.newBuilder().setIntegerValue(price).build());

        List<String> normalized = (keywords == null) ? List.of()
                : keywords.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        if (!normalized.isEmpty()) {
            point.putPayload("keywords", toStringArrayValue(normalized));
        }

        Points.UpsertPoints upsert = Points.UpsertPoints.newBuilder()
                .setCollectionName(qdrantProps.getCollection())
                .addPoints(point.build())
                .build();

        qdrant.upsertAsync(upsert).get(10, TimeUnit.SECONDS);
    }

    @Deprecated(forRemoval = true)
    public void upsertProduct(Long productId, String title, long price) throws Exception {
        log.warn("[DEPRECATED] Legacy upsertProduct() 호출됨: id={}, title={}", productId, title);
    }
}
