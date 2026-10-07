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
import java.util.stream.Collectors;

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

    public void upsertProduct(Long productId,
                              String title,
                              long price,
                              String category,
                              String shortDescription,
                              List<String> keywords) throws Exception {
        StringBuilder sb = new StringBuilder();

        if (title != null && !title.isBlank()) {
            sb.append(title.trim());
        }

        if (keywords != null && !keywords.isEmpty()) {
            sb.append(" ");
            sb.append(
                    keywords.stream()
                            .filter(Objects::nonNull)
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .distinct()
                            .reduce((a, b) -> a + " " + b)
                            .orElse("")
            );
        }

        if (category != null && !category.isBlank()) {
            sb.append(" ").append(category.trim());
        }

        if (shortDescription != null && !shortDescription.isBlank()) {
            sb.append(" ").append(shortDescription.trim());
        }

        String textForEmbedding = sb.toString().trim();
        if (textForEmbedding.isEmpty()) {
            log.warn("[VECTOR] upsert skip - no text to embed. productId={}", productId);
            return;
        }

        List<Float> vec = embeddingService.embed(textForEmbedding);

        Points.PointStruct.Builder point = Points.PointStruct.newBuilder()
                .setId(Points.PointId.newBuilder().setNum(productId))
                .setVectors(
                        Points.Vectors.newBuilder()
                                .setVector(
                                        Points.Vector.newBuilder()
                                                .addAllData(toFloatList(vec))
                                                .build()
                                )
                                .build()
                )
                .putPayload("productId", JsonWithInt.Value.newBuilder().setIntegerValue(productId).build())
                .putPayload("title", JsonWithInt.Value.newBuilder().setStringValue(title).build())
                .putPayload("price", JsonWithInt.Value.newBuilder().setIntegerValue(price).build());

        if (category != null && !category.isBlank()) {
            point.putPayload("category", JsonWithInt.Value.newBuilder().setStringValue(category).build());
        }

        if (shortDescription != null && !shortDescription.isBlank()) {
            point.putPayload(
                    "shortDescription",
                    JsonWithInt.Value.newBuilder().setStringValue(shortDescription.trim()).build()
            );
        }

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

    /*
     * Qdrant에서 상품 벡터(포인트) 삭제
     */
    public void deleteProduct(Long productId) throws Exception {
        if (productId == null) {
            log.warn("[VECTOR] delete skip - productId is null");
            return;
        }

        Points.PointId pid = Points.PointId.newBuilder()
                .setNum(productId)
                .build();

        Points.PointsIdsList idsList = Points.PointsIdsList.newBuilder()
                .addIds(pid)
                .build();

        Points.PointsSelector selector = Points.PointsSelector.newBuilder()
                .setPoints(idsList)
                .build();

        Points.DeletePoints delete = Points.DeletePoints.newBuilder()
                .setCollectionName(qdrantProps.getCollection())
                .setPoints(selector)
                .build();

        qdrant.deleteAsync(delete).get(10, TimeUnit.SECONDS);
        log.info("[VECTOR] delete ok - productId={}", productId);
    }


    /**
     * 디버그/검증용: Qdrant에서 검색 후 (productId, score) 반환
     * - score는 Qdrant가 주는 raw score 그대로 반환
     * - threshold가 null이면 scoreThreshold(컷) 적용하지 않음
     */
    public List<VectorProductSearch.ScoredId> searchWithScores(
            String query,
            Integer minPrice,
            Integer maxPrice,
            String age,
            String gender,
            int limit,
            Double threshold   // ✅ nullable
    ) throws Exception {

        if (query == null || query.isBlank()) {
            return List.of();
        }

        String q = query.trim();
        List<Float> qvec = embeddingService.embed(q);

        // ---- filter (현재는 price만) ----
        Points.Filter.Builder filter = Points.Filter.newBuilder();
        List<Points.Condition> must = new ArrayList<>();

        if (minPrice != null || maxPrice != null) {
            Points.Range.Builder range = Points.Range.newBuilder();
            if (minPrice != null) range.setGte(minPrice);
            if (maxPrice != null) range.setLte(maxPrice);

            must.add(
                    Points.Condition.newBuilder()
                            .setField(
                                    Points.FieldCondition.newBuilder()
                                            .setKey("price")
                                            .setRange(range.build())
                                            .build()
                            )
                            .build()
            );
        }

        Points.SearchPoints.Builder req = Points.SearchPoints.newBuilder()
                .setCollectionName(qdrantProps.getCollection())
                .addAllVector(toFloatList(qvec))
                .setLimit(limit);

        if (!must.isEmpty()) {
            filter.addAllMust(must);
            req.setFilter(filter.build());
        }

        // ✅ threshold는 "있을 때만" 적용 (디버그에서 컷 없이 raw score 보기 가능)
        if (threshold != null) {
            req.setScoreThreshold(threshold.floatValue());
        }

        List<Points.ScoredPoint> resp =
                qdrant.searchAsync(req.build()).get(10, TimeUnit.SECONDS);

        // ✅ 디버그용 로그(원인 파악에 도움)
        if (log.isDebugEnabled()) {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (Points.ScoredPoint p : resp) {
                min = Math.min(min, p.getScore());
                max = Math.max(max, p.getScore());
            }
            log.debug("[VECTOR][DEBUG] q='{}' hits={} threshold={} score[min={}, max={}]",
                    q, resp.size(), threshold, (resp.isEmpty() ? "-" : min), (resp.isEmpty() ? "-" : max));
        }

        return resp.stream()
                .map(r -> new VectorProductSearch.ScoredId(
                        r.getId().getNum(),
                        r.getScore()
                ))
                .collect(Collectors.toList());
    }



    @Deprecated(forRemoval = true)
    public void upsertProduct(Long productId, String title, long price) throws Exception {
        log.warn("[DEPRECATED] Legacy upsertProduct() 호출됨: id={}, title={}", productId, title);
    }
}
