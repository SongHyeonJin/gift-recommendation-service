package com.example.giftrecommender.domain.repository;

import com.example.giftrecommender.domain.entity.CrawlingProduct;
import com.example.giftrecommender.domain.entity.QCrawlingProduct;
import com.example.giftrecommender.domain.enums.Age;
import com.example.giftrecommender.domain.enums.Gender;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CrawlingProductQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 벡터 hitIds 등 특정 ID 목록을 조건 필터와 함께 조회할 때 사용
     */
    public List<CrawlingProduct> searchByIdsAndConditions(
            List<Long> idsInOrder,
            String keyword,
            Integer minPrice,
            Integer maxPrice,
            String category,
            String platform,
            String sellerName,
            Gender gender,
            Age age,
            Boolean isConfirmed
    ) {
        QCrawlingProduct p = QCrawlingProduct.crawlingProduct;

        BooleanBuilder builder = new BooleanBuilder();

        builder.and(p.id.in(idsInOrder));

        if (keyword != null && !keyword.isBlank()) {
            String q = keyword.trim();

            builder.and(
                    p.originalName.containsIgnoreCase(q)
                            .or(p.displayName.containsIgnoreCase(q))
                            .or(p.category.containsIgnoreCase(q))
            );
        }

        if (minPrice != null) builder.and(p.price.goe(minPrice));
        if (maxPrice != null) builder.and(p.price.loe(maxPrice));
        if (category != null && !category.isBlank()) builder.and(p.category.containsIgnoreCase(category));
        if (platform != null && !platform.isBlank()) builder.and(p.platform.eq(platform));
        if (sellerName != null && !sellerName.isBlank()) builder.and(p.sellerName.containsIgnoreCase(sellerName));
        if (gender != null) builder.and(p.gender.eq(gender));
        if (age != null) builder.and(p.age.eq(age));
        if (isConfirmed != null) builder.and(p.isConfirmed.eq(isConfirmed));

        return queryFactory.selectFrom(p)
                .where(builder)
                .fetch();
    }

    /**
     * 검색 API용:
     * - query / queryNoSpace 둘 중 하나라도 keywords/title/category에 매칭되면 포함
     * - (queryNoSpace) 는 "전기 포트" -> "전기포트" 처럼 공백 제거한 검색을 커버하기 위함
     * - limit으로 과도한 fetch 방지
     */
    public List<CrawlingProduct> searchByKeywordOrNameOrCategory(
            String query,
            String queryNoSpace,
            Integer minPrice,
            Integer maxPrice,
            String category,
            String platform,
            String sellerName,
            Gender gender,
            Age age,
            Boolean isConfirmed,
            int limit
    ) {
        QCrawlingProduct p = QCrawlingProduct.crawlingProduct;

        BooleanBuilder builder = new BooleanBuilder();

        BooleanExpression keywordExpr = buildKeywordMatchExpr(p, query, queryNoSpace);
        if (keywordExpr != null) {
            builder.and(keywordExpr);
        }

        if (minPrice != null) builder.and(p.price.goe(minPrice));
        if (maxPrice != null) builder.and(p.price.loe(maxPrice));
        if (category != null && !category.isBlank()) builder.and(p.category.containsIgnoreCase(category));
        if (platform != null && !platform.isBlank()) builder.and(p.platform.eq(platform));
        if (sellerName != null && !sellerName.isBlank()) builder.and(p.sellerName.containsIgnoreCase(sellerName));
        if (gender != null) builder.and(p.gender.eq(gender));
        if (age != null) builder.and(p.age.eq(age));
        if (isConfirmed != null) builder.and(p.isConfirmed.eq(isConfirmed));

        int safeLimit = (limit <= 0) ? 200 : Math.min(limit, 500);

        return queryFactory.selectFrom(p)
                .where(builder)
                .orderBy(p.createdAt.desc(), p.id.desc())
                .limit(safeLimit)
                .fetch();
    }

    /**
     * keywords/title/category에 대해:
     * 1. 일반 containsIgnoreCase(query)
     * 2. 공백 제거 후 contains(queryNoSpace)
     *
     * Querydsl에서 REPLACE 등을 쓰기 위해 stringTemplate 사용
     * - MySQL 기준: replace(col, ' ', '')
     *
     * 주의: 공백 제거는 lower() 적용도 같이 하는게 안정적이라
     * lower(replace(col,' ','')) like %queryNoSpaceLower%
     */
    private BooleanExpression buildKeywordMatchExpr(QCrawlingProduct p, String query, String queryNoSpace) {
        boolean hasQuery = (query != null && !query.isBlank());
        boolean hasNoSpace = (queryNoSpace != null && !queryNoSpace.isBlank());

        if (!hasQuery && !hasNoSpace) return null;

        BooleanExpression expr = null;

        if (hasQuery) {
            String q = query.trim();

            // 기본 매칭
            BooleanExpression base =
                    p.keywords.any().containsIgnoreCase(q)
                            .or(p.displayName.containsIgnoreCase(q))
                            .or(p.originalName.containsIgnoreCase(q))
                            .or(p.category.containsIgnoreCase(q));

            expr = base;
        }

        if (hasNoSpace) {
            String qns = queryNoSpace.trim().toLowerCase();

            // title 공백 제거 매칭
            BooleanExpression titleNoSpace = likeNoSpaceLower(p.displayName, qns)
                    .or(likeNoSpaceLower(p.originalName, qns))
                    .or(likeNoSpaceLower(p.category, qns));

            BooleanExpression keywordsNoSpace = likeNoSpaceLower(p.keywords.any(), qns);

            BooleanExpression noSpaceExpr = keywordsNoSpace.or(titleNoSpace);

            expr = (expr == null) ? noSpaceExpr : expr.or(noSpaceExpr);
        }

        return expr;
    }

    /**
     * lower(replace(target,' ','')) like %qns%
     *
     * - Querydsl stringTemplate로 DB 함수 호출
     * - target이 null이면 결과는 null이 될 수 있으므로 coalesce로 방어
     */
    private BooleanExpression likeNoSpaceLower(com.querydsl.core.types.dsl.StringPath target, String qnsLowerNoSpace) {
        // coalesce(target,'') → replace(...,' ','') → lower(...) → like '%qns%'
        return Expressions.stringTemplate(
                "lower(replace(coalesce({0}, ''), ' ', ''))",
                target
        ).like("%" + escapeLike(qnsLowerNoSpace) + "%");
    }

    /**
     * LIKE 특수문자(%, _)가 query에 들어올 때 오동작 방지용
     * - MySQL 기본 ESCAPE 미지정이면 완전한 방어는 어렵지만 최소한 \로 치환해서 안전도를 올림
     */
    private String escapeLike(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
