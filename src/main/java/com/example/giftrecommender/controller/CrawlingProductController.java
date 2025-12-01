package com.example.giftrecommender.controller;

import com.example.giftrecommender.common.BasicResponseDto;
import com.example.giftrecommender.domain.enums.Age;
import com.example.giftrecommender.domain.enums.Gender;
import com.example.giftrecommender.dto.request.*;
import com.example.giftrecommender.dto.request.age.AgeBulkRequestDto;
import com.example.giftrecommender.dto.request.age.AgeRequestDto;
import com.example.giftrecommender.dto.request.confirm.ConfirmBulkRequestDto;
import com.example.giftrecommender.dto.request.confirm.ConfirmRequestDto;
import com.example.giftrecommender.dto.request.gender.GenderBulkRequestDto;
import com.example.giftrecommender.dto.request.gender.GenderRequestDto;
import com.example.giftrecommender.dto.request.product.CrawlingProductRequestDto;
import com.example.giftrecommender.dto.request.product.CrawlingProductUpdateRequestDto;
import com.example.giftrecommender.dto.request.product.ProductKeywordBulkSaveRequest;
import com.example.giftrecommender.dto.request.product.ProductKeywordBulkUpdateRequest;
import com.example.giftrecommender.dto.response.*;
import com.example.giftrecommender.dto.response.age.AgeBulkResponseDto;
import com.example.giftrecommender.dto.response.age.AgeResponseDto;
import com.example.giftrecommender.dto.response.confirm.ConfirmBulkResponseDto;
import com.example.giftrecommender.dto.response.confirm.ConfirmResponseDto;
import com.example.giftrecommender.dto.response.gender.GenderBulkResponseDto;
import com.example.giftrecommender.dto.response.gender.GenderResponseDto;
import com.example.giftrecommender.dto.response.product.CrawlingProductBulkSaveResponseDto;
import com.example.giftrecommender.dto.response.product.CrawlingProductResponseDto;
import com.example.giftrecommender.dto.response.product.ProductKeywordBulkSaveResponse;
import com.example.giftrecommender.service.CrawlingProductSaver;
import com.example.giftrecommender.service.CrawlingProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    private final CrawlingProductSaver crawlingProductSaver;

    @Operation(summary = "크롤링 상품 저장")
    @PostMapping
    public ResponseEntity<BasicResponseDto<CrawlingProductResponseDto>> save(@RequestBody CrawlingProductRequestDto requestDto) {
        return ResponseEntity.ok(BasicResponseDto.success("크롤링 상품 저장 완료.", crawlingProductSaver.save(requestDto)));
    }

    @Operation(summary = "크롤링 상품 여러 건 저장(부분 성공 정책)")
    @PostMapping("/bulk")
    public ResponseEntity<BasicResponseDto<CrawlingProductBulkSaveResponseDto>> saveAll(
            @RequestBody List<CrawlingProductRequestDto> requestDtoList
    ) {
        return ResponseEntity.ok(
                BasicResponseDto.success("크롤링 상품 여러 건 저장완료(부분 성공 정책)", crawlingProductService.saveAll(requestDtoList))
        );
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

    @Operation(summary = "크롤링 상품 상세 조회")
    @GetMapping("/{product_id}")
    public ResponseEntity<BasicResponseDto<CrawlingProductResponseDto>> getProduct(
            @PathVariable(name = "product_id") Long productId
    ) {
        CrawlingProductResponseDto result = crawlingProductService.getProduct(productId);
        return ResponseEntity.ok(
                BasicResponseDto.success("크롤링 상품 상세 조회 완료.", result)
        );
    }

    @Operation(summary = "상품 단건 부분 수정 (PATCH)")
    @PatchMapping("/{product_id}")
    public ResponseEntity<BasicResponseDto<CrawlingProductResponseDto>> patchProduct(
            @PathVariable(name = "product_id") Long productId,
            @Valid @RequestBody CrawlingProductUpdateRequestDto requestDto
    ) {
        return ResponseEntity.ok(BasicResponseDto.success("상품이 수정되었습니다.", crawlingProductService.updateProduct(productId, requestDto)));
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

    @Operation(summary = "관리자 컨펌 상태 일괄 변경")
    @PutMapping("/confirm/bulk")
    public ResponseEntity<BasicResponseDto<ConfirmBulkResponseDto>> updateConfirmStatusBulk(
            @RequestBody ConfirmBulkRequestDto request
    ) {
        ConfirmBulkResponseDto result = crawlingProductService.updateConfirmStatusBulk(request);
        return ResponseEntity.ok(
                BasicResponseDto.success("관리자 컨펌 상태 일괄 변경 완료.", result)
        );
    }

    @Operation(summary = "상품 연령대 단건 변경")
    @PutMapping("/{product_id}/age")
    public ResponseEntity<BasicResponseDto<AgeResponseDto>> updateAge(
            @Valid @RequestBody AgeRequestDto request
    ) {
        AgeResponseDto result = crawlingProductService.updateAge(request);
        return ResponseEntity.ok(BasicResponseDto.success("연령대 변경 완료", result));
    }

    @Operation(summary = "상품 연령대 일괄 변경")
    @PutMapping("/age/bulk")
    public ResponseEntity<BasicResponseDto<AgeBulkResponseDto>> updateAgeBulk(
            @Valid @RequestBody AgeBulkRequestDto request
    ) {
        AgeBulkResponseDto result = crawlingProductService.updateAgeBulk(request);
        return ResponseEntity.ok(BasicResponseDto.success("연령대 일괄 변경 완료", result));
    }

    @Operation(summary = "상품 성별 단건 변경")
    @PutMapping("/{product_id}/gender")
    public ResponseEntity<BasicResponseDto<GenderResponseDto>> updateGender(
            @Valid @RequestBody GenderRequestDto request
    ) {
        GenderResponseDto result = crawlingProductService.updateGender(request);
        return ResponseEntity.ok(BasicResponseDto.success("성별 변경 완료", result));
    }

    @Operation(summary = "상품 성별 일괄 변경")
    @PutMapping("/gender/bulk")
    public ResponseEntity<BasicResponseDto<GenderBulkResponseDto>> updateGenderBulk(
            @Valid @RequestBody GenderBulkRequestDto request
    ) {
        GenderBulkResponseDto result = crawlingProductService.updateGenderBulk(request);
        return ResponseEntity.ok(BasicResponseDto.success("성별 일괄 변경 완료", result));
    }

    @Operation(
            summary = "여러 크롤링 상품에 키워드를 일괄 저장",
            description = """
                    관리자 페이지에서 여러 크롤링 상품을 선택한 뒤,
                    공통 키워드 목록을 한 번에 저장할 때 사용하는 API입니다.
                    """
    )
    @PostMapping("/keywords/bulk-add")
    public ResponseEntity<BasicResponseDto<ProductKeywordBulkSaveResponse>> saveKeywordsInBulk(
            @Valid @RequestBody ProductKeywordBulkSaveRequest request
    ) {
        ProductKeywordBulkSaveResponse response = crawlingProductService.saveKeywordsBulk(request);
        return ResponseEntity.ok(BasicResponseDto.success("선택한 상품에 키워드가 성공적으로 추가되었습니다.", response));
    }

    @Operation(
            summary = "여러 크롤링 상품의 키워드를 일괄 수정(덮어쓰기)",
            description = """
                선택된 여러 상품의 기존 키워드를 모두 삭제하고
                입력된 키워드 목록으로 덮어씌우는 관리자용 API입니다.
                """
    )
    @PostMapping("/keywords/bulk-update")
    public ResponseEntity<BasicResponseDto<ProductKeywordBulkSaveResponse>> updateKeywordsInBulk(
            @Valid @RequestBody ProductKeywordBulkUpdateRequest request
    ) {
        ProductKeywordBulkSaveResponse response = crawlingProductService.updateKeywordsBulk(request);
        return ResponseEntity.ok(
                BasicResponseDto.success("선택한 상품의 키워드가 성공적으로 수정되었습니다.", response)
        );
    }

}
