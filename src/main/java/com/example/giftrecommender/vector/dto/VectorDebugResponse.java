package com.example.giftrecommender.vector.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Schema(description = "벡터 검색 디버그 응답 (Qdrant raw score + similarity 변환값 포함)")
public class VectorDebugResponse {

    @Schema(description = "검색어", example = "이어폰")
    private String query;

    @Schema(description = "요청에 사용된 threshold 값 (scoreThreshold)", example = "0.65")
    private Double threshold;

    @Schema(description = "Qdrant score가 similarity인지 distance인지 자동 판별 결과", example = "SIMILARITY")
    private ScoreType scoreType;

    @Schema(description = "벡터 검색 결과 리스트 (Qdrant score 기준 정렬)")
    private List<VectorDebugItem> results;

    @Schema(description = "Qdrant score 타입")
    public enum ScoreType {
        @Schema(description = "score가 유사도(클수록 유사)")
        SIMILARITY,

        @Schema(description = "score가 거리(작을수록 유사)")
        DISTANCE,

        @Schema(description = "판별이 애매한 경우")
        UNKNOWN
    }

    @Getter
    @AllArgsConstructor
    @Schema(description = "벡터 검색 결과 아이템")
    public static class VectorDebugItem {

        @Schema(description = "상품 ID (DB PK = Qdrant pointId)", example = "357")
        private Long productId;

        @Schema(description = "Qdrant에서 반환된 raw score (그대로)", example = "0.84114355")
        private Double rawScore;

        @Schema(description = """
                similarity로 환산한 값.
                - scoreType=SIMILARITY면 rawScore와 동일
                - scoreType=DISTANCE면 (1 - rawScore)
                - scoreType=UNKNOWN이면 rawScore 그대로 표시
                """, example = "0.84114355")
        private Double similarity;

        @Schema(description = "표시용 상품명", example = "노이즈캔슬링 블루투스 이어폰")
        private String displayName;

        @Schema(description = "카테고리", example = "디지털/가전 > 음향기기 > 이어폰")
        private String category;

        @Schema(description = "짧은 설명(벡터에 포함된 텍스트일 수 있음)", example = "노이즈 캔슬링, 블루투스 5.3, 배터리 24시간")
        private String shortDescription;

        @Schema(description = "가격(원)", example = "69000")
        private Integer price;

        @Schema(description = "DB에 저장된 키워드 목록", example = "[\"이어폰\",\"블루투스\",\"노이즈캔슬링\"]")
        private List<String> keywords;
    }
}
