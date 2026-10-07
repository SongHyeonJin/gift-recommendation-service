package com.example.giftrecommender.service;

import com.example.giftrecommender.common.exception.ErrorException;
import com.example.giftrecommender.common.exception.ExceptionEnum;
import com.example.giftrecommender.domain.entity.CrawlingProduct;
import com.example.giftrecommender.domain.repository.CrawlingProductRepository;
import com.example.giftrecommender.dto.request.ProductMetadataRequest;
import com.example.giftrecommender.dto.response.ProductMetadataResponse;
import com.example.giftrecommender.vector.ProductVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductMetadataService {

    private final CrawlingProductRepository crawlingProductRepository;
    private final ObjectProvider<ProductVectorService> productVectorServiceProvider;

    @Transactional(readOnly = true)
    public ProductMetadataResponse getMetadata(Long productId) {
        CrawlingProduct product = crawlingProductRepository.findById(productId)
                .orElseThrow(() -> new ErrorException(ExceptionEnum.PRODUCT_NOT_FOUND));

        List<String> keywords = crawlingProductRepository.findKeywordsById(productId);

        return ProductMetadataResponse.builder()
                .productId(product.getId())
                .category(product.getCategory())
                .shortDescription(product.getShortDescription())
                .keywords(keywords == null ? Collections.emptyList() : keywords)
                .vectorUpdated(false)
                .build();
    }

    @Transactional
    public ProductMetadataResponse updateMetadata(Long productId, ProductMetadataRequest request) {
        CrawlingProduct product = crawlingProductRepository.findById(productId)
                .orElseThrow(() -> new ErrorException(ExceptionEnum.PRODUCT_NOT_FOUND));

        // DB 업데이트
        if (request.category() != null && !request.category().isBlank()) {
            product.changeCategory(normalizeCategory(request.category()));
        }
        if (request.shortDescription() != null && !request.shortDescription().isBlank()) {
            product.changeShortDescription(request.shortDescription().trim());
        }

        if (request.keywords() != null) {
            List<String> normalized = normalizeKeywords(request.keywords());
            replaceKeywords(product, normalized);
        }

        crawlingProductRepository.save(product);

        // Qdrant 업데이트
        boolean vectorUpdated = false;

        ProductVectorService productVectorService = productVectorServiceProvider.getIfAvailable();

        if (productVectorService == null) {
            log.debug("[VECTOR] ProductVectorService bean not found. skip upsert. productId={}", product.getId());
        } else {
            try {
                String title = resolveTitle(product);
                if (title != null && !title.isBlank()) {
                    long price = resolvePrice(product);
                    String category = product.getCategory();
                    String shortDescription = product.getShortDescription();
                    List<String> keywordsForVector = crawlingProductRepository.findKeywordsById(product.getId());

                    productVectorService.upsertProduct(
                            product.getId(),
                            title,
                            price,
                            category,
                            shortDescription,
                            keywordsForVector
                    );
                    vectorUpdated = true;
                } else {
                    log.warn("[VECTOR] skip - title empty. productId={}", product.getId());
                }
            } catch (Exception e) {
                log.warn("[VECTOR] upsert failed. productId={}, cause={}", product.getId(), e.getMessage(), e);
                vectorUpdated = false;
            }
        }

        List<String> resultKeywords = crawlingProductRepository.findKeywordsById(product.getId());

        return ProductMetadataResponse.builder()
                .productId(product.getId())
                .category(product.getCategory())
                .shortDescription(product.getShortDescription())
                .keywords(resultKeywords == null ? Collections.emptyList() : resultKeywords)
                .vectorUpdated(vectorUpdated)
                .build();
    }

    private String normalizeCategory(String category) {
        String trimmed = category.trim();
        return trimmed.replace(" > ", ">").replace(">", ">");
    }

    private List<String> normalizeKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return Collections.emptyList();
        }
        return keywords.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private void replaceKeywords(CrawlingProduct product, List<String> normalizedKeywords) {
        if (product.getKeywords() == null) {
            return;
        }
        product.getKeywords().clear();
        product.getKeywords().addAll(normalizedKeywords);
    }

    private String resolveTitle(CrawlingProduct product) {
        String title = product.getDisplayName();
        if (title == null || title.isBlank()) {
            title = product.getOriginalName();
        }
        return title;
    }

    private long resolvePrice(CrawlingProduct product) {
        if (product.getPrice() == null) {
            return 0L;
        }
        return product.getPrice().longValue();
    }
}
