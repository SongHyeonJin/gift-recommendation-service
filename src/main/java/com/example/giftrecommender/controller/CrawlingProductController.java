package com.example.giftrecommender.controller;

import com.example.giftrecommender.common.BasicResponseDto;
import com.example.giftrecommender.domain.enums.Age;
import com.example.giftrecommender.domain.enums.Gender;
import com.example.giftrecommender.dto.request.ConfirmRequestDto;
import com.example.giftrecommender.dto.request.CrawlingProductRequestDto;
import com.example.giftrecommender.dto.request.ScoreRequestDto;
import com.example.giftrecommender.dto.response.ConfirmResponseDto;
import com.example.giftrecommender.dto.response.CrawlingProductResponseDto;
import com.example.giftrecommender.dto.response.ScoreResponseDto;
import com.example.giftrecommender.service.CrawlingProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "크롤링 상품", description = "크롤링 상품 관련 API")
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class CrawlingProductController {

    private final CrawlingProductService crawlingProductService;

    @Operation(summary = "크롤링 상품 저장")
    @PostMapping
    public ResponseEntity<BasicResponseDto<CrawlingProductResponseDto>> save(@RequestBody CrawlingProductRequestDto requestDto) {
        return ResponseEntity.ok(BasicResponseDto.success("크롤링 상품 저장 완료.", crawlingProductService.save(requestDto)));
    }

    @Operation(summary = "크롤링 상품 여러 건 저장")
    @PostMapping("/batch")
    public ResponseEntity<List<CrawlingProductResponseDto>> saveAll(@RequestBody List<CrawlingProductRequestDto> requestDtoList) {
        return ResponseEntity.ok(crawlingProductService.saveAll(requestDtoList));
    }

    @Operation(summary = "크롤링 상품 목록 조회 (필터/정렬/페이징)")
    @GetMapping
    public ResponseEntity<BasicResponseDto<Page<CrawlingProductResponseDto>>> getProducts(
            @RequestParam(name = "keyword",     required = false) String keyword,
            @RequestParam(name = "minPrice",    required = false) Integer minPrice,
            @RequestParam(name = "maxPrice",    required = false) Integer maxPrice,
            @RequestParam(name = "category",    required = false) String category,
            @RequestParam(name = "platform",    required = false) String platform,
            @RequestParam(name = "sellerName",  required = false) String sellerName,
            @RequestParam(name = "gender",      required = false) Gender gender,
            @RequestParam(name = "age",         required = false) Age age,
            @RequestParam(name = "isConfirmed", required = false) Boolean isConfirmed,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<CrawlingProductResponseDto> page = crawlingProductService.getProducts(
                keyword, minPrice, maxPrice, category, platform, sellerName, gender, age, isConfirmed, pageable
        );
        return ResponseEntity.ok(
                BasicResponseDto.success("크롤링 상품 목록 조회 완료.", page)
        );
    }

    @Operation(summary = "관리자 점수 부여 (adminCheck 자동 true)")
    @PostMapping("/{product_id}/score")
    public ResponseEntity<BasicResponseDto<ScoreResponseDto>> giveScore(
            @PathVariable(name = "product_id") Long productId,
            @RequestBody ScoreRequestDto requestDto
    ) {
        return ResponseEntity.ok(
                BasicResponseDto.success("관리자 점수 부여 완료.", crawlingProductService.giveScore(productId, requestDto))
        );
    }

    @Operation(summary = "관리자 컨펌 상태 변경")
    @PutMapping("/{product_id}/confirm")
    public ResponseEntity<BasicResponseDto<ConfirmResponseDto>> updateConfirmStatus(
            @PathVariable(name = "product_id") Long productId,
            @RequestBody ConfirmRequestDto requestDto
    ) {
        return ResponseEntity.ok(
                BasicResponseDto.success("관리자 컨펌 상태 변경 완료.", crawlingProductService.updateConfirmStatus(productId, requestDto))
        );
    }

}
