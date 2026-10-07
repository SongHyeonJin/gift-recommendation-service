package com.example.giftrecommender.controller;

import com.example.giftrecommender.common.BasicResponseDto;
import com.example.giftrecommender.dto.request.ProductMetadataRequest;
import com.example.giftrecommender.dto.response.ProductMetadataResponse;
import com.example.giftrecommender.service.ProductMetadataService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/products")
public class ProductMetadataController {

    private final ProductMetadataService adminProductMetadataService;

    @Operation(summary = "관리자 상품 메타데이터 조회", description = "상품의 category/shortDescription/keywords를 조회합니다.")
    @GetMapping("/{productId}/metadata")
    public ResponseEntity<BasicResponseDto<ProductMetadataResponse>> getMetadata(
            @PathVariable Long productId
    ) {
        ProductMetadataResponse response = adminProductMetadataService.getMetadata(productId);
        return ResponseEntity.ok(BasicResponseDto.success("조회 완료", response));
    }

    @Operation(summary = "관리자 상품 메타데이터 수정", description = "승인된 category/shortDescription/keywords를 저장하고, 저장 성공 시 Qdrant 벡터도 업데이트합니다.")
    @PutMapping("/{productId}/metadata")
    public ResponseEntity<BasicResponseDto<ProductMetadataResponse>> updateMetadata(
            @PathVariable Long productId,
            @Valid @RequestBody ProductMetadataRequest request
    ) {
        ProductMetadataResponse response = adminProductMetadataService.updateMetadata(productId, request);
        return ResponseEntity.ok(BasicResponseDto.success("수정 완료", response));
    }
}
