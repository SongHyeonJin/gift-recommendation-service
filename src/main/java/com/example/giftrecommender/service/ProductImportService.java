package com.example.giftrecommender.service;

import com.example.giftrecommender.domain.entity.Product;
import com.example.giftrecommender.domain.entity.keyword.KeywordGroup;
import com.example.giftrecommender.domain.repository.ProductRepository;
import com.example.giftrecommender.domain.repository.keyword.KeywordGroupRepository;
import com.example.giftrecommender.dto.response.ProductResponseDto;
import com.example.giftrecommender.infra.naver.NaverApiClient;
import com.example.giftrecommender.util.RecommendationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImportService {

    private final NaverApiClient naverApiClient;
    private final ProductRepository productRepository;
    private final KeywordGroupRepository keywordGroupRepository;
    private static final int MAX_COMBOS = 50;

    @Transactional
    public void importUntilEnough(List<String> tagKeywords, String priceKeyword, String receiverKeyword,
                                  String reasonKeyword, int neededCount) {
        if (tagKeywords == null || tagKeywords.size() < 2) {
            log.warn("태그 키워드는 2개 이상 필요합니다.");
            return;
        }

        // 1. KeywordGroup 미리 저장
        List<String> allKeywords = new ArrayList<>(tagKeywords);
        if (!receiverKeyword.isBlank()) allKeywords.add(receiverKeyword);
        if (!reasonKeyword.isBlank())   allKeywords.add(reasonKeyword);

        List<KeywordGroup> groups = keywordGroupRepository.findByMainKeywordIn(allKeywords);
        Set<String> exist = groups.stream()
                .map(KeywordGroup::getMainKeyword)
                .collect(Collectors.toSet());

        List<KeywordGroup> newGroups = allKeywords.stream()
                .filter(k -> !exist.contains(k))
                .map(KeywordGroup::new)
                .toList();

        keywordGroupRepository.saveAll(newGroups);
        groups.addAll(newGroups);

        // 2. 우선순위 콤보 생성
        List<List<String>> combos = RecommendationUtil.generatePriorityCombos(tagKeywords, receiverKeyword, reasonKeyword);

        Set<String> searched = new HashSet<>();
        Set<String> seenTitles = new HashSet<>();
        List<Product> toSave = new ArrayList<>();
        Map<String, Product> distinctKeyMap = new LinkedHashMap<>();
        Set<String> brandSet = new HashSet<>();

        if (combos.size() > MAX_COMBOS) {
            combos = combos.subList(0, MAX_COMBOS);
        }

        for (List<String> combo : combos) {
            String query = String.join(" ", combo);
            if (!searched.add(query)) continue;

            log.info("검색 콤보: '{}'", query);
            for (int page = 1; page <= 10; page++) {
                log.info("API 호출: '{}', page={}", query, page);
                List<ProductResponseDto> items = naverApiClient.search(query, page, 100);
                if (items.isEmpty()) break;

                Set<String> links = items.stream()
                        .map(ProductResponseDto::link)
                        .collect(Collectors.toSet());
                Set<String> existingLinks = productRepository.findLinksIn(links);

                for (ProductResponseDto dto : items) {
                    // 이미 DB에 있는 링크면 skip
                    if (existingLinks.contains(dto.link())) continue;

                    //  제목이 이미 처리된 적 있으면 skip
                    if (!seenTitles.add(dto.title())) continue;

                    List<KeywordGroup> matched = groups.stream()
                            .filter(g -> combo.contains(g.getMainKeyword()))
                            .toList();

                    Product p = Product.from(dto, matched);
                    toSave.add(p);

                    if (matchesPrice(p.getPrice(), priceKeyword)) {
                        log.info("가격 필터 통과: {}", p.getPrice());

                        String brand = RecommendationUtil.extractBrand(p.getTitle(), p.getMallName());
                        String baseTitle = RecommendationUtil.extractBaseTitle(p.getTitle());
                        String key = baseTitle + "::" + p.getImageUrl();

                        if (brandSet.contains(brand)) continue;

                        brandSet.add(brand);
                        distinctKeyMap.putIfAbsent(key, p);

                        if (distinctKeyMap.size() >= neededCount) {
                            productRepository.saveAll(toSave);
                            log.info("저장 완료 - 고유 상품 수: {}, 가격 필터 통과: {}",
                                    distinctKeyMap.size(), countPriceMatched(toSave, priceKeyword));
                            return;
                        }
                    }
                }
                if (items.size() < 100) break;
            }
        }
    }

    private long countPriceMatched(List<Product> products, String priceKeyword) {
        return products.stream().filter(p -> matchesPrice(p.getPrice(), priceKeyword)).count();
    }

    private boolean matchesPrice(int price, String priceKeyword) {
        return switch (priceKeyword) {
            case "1만원 이하" -> price <= 10_000;
            case "1~3만원"   -> price >= 10_000 && price <= 30_000;
            case "3~5만원"   -> price >= 30_000 && price <= 50_000;
            case "5~10만원"  -> price >= 50_000 && price <= 100_000;
            case "10~30만원" -> price >= 100_000 && price <= 300_000;
            case "30~50만원" -> price >= 300_000 && price <= 500_000;
            default          -> false;
        };
    }


}