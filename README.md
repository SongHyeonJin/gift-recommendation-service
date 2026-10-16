# Gift Recommendation Service

AI 기반 맞춤형 선물 추천 서비스

설문 응답(관계·연령·성별·가격대·선물 이유·취향·키워드)을 받아, DB 키워드 검색과 OpenAI 임베딩 + Qdrant 벡터 검색을 결합해 선물 8개를 추천합니다.

> v2 리팩토링 진행 중입니다. 측정 결과와 설계 결정은 [`docs/v2/`](docs/v2/)에 기록합니다.

## 기술 스택

| 분류 | 기술 |
|------|------|
| **Backend** | Java 21, Spring Boot 3.2.5, Spring Data JPA, Querydsl 5.0 |
| **AI** | OpenAI `text-embedding-3-small` (상품·질의 임베딩) |
| **Vector DB** | Qdrant 1.15 — 검색·payload 갱신은 REST(WebClient), 컬렉션 생성·포인트 업서트/삭제는 gRPC(Java client) |
| **Database / Cache** | MySQL 8.x, Redis (외부 API 쿼터), Caffeine (임베딩 캐시) |
| **Infra / DevOps** | Docker, GitHub Actions CI (MySQL·Redis 서비스 컨테이너), AWS EC2 (CD 비활성) |
| **Docs** | SpringDoc OpenAPI (Swagger UI) |

## 아키텍처

![Sequence Diagram](docs/sequence_diagram.png)

## 주요 기능

### 1. 벡터 기반 선물 추천 (`RecommendationVectorService`)
- 키워드별 후보 수집: **DB 키워드 검색 → 벡터 검색(DB에 존재하는 상품만) → 외부(네이버) 보충**
- 키워드별 2개를 우선 확보한 뒤 여분을 재분배하고, 8개 미만이면 전역 보충 (최대 8개)
- 성별·가격·유아 도메인 가드, Jaccard 유사도 기반 유사 상품 중복 제거
- Qdrant 필터: 가격 범위 + 연령/성별 match

### 2. 상품 관리와 벡터 동기화
- 상품 저장 시 Qdrant 직접 동기화 + `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` 리스너로 임베딩 업서트 (지수 백오프 재시도)
- 상품 삭제 시 `ProductDeletedEvent` → 커밋 이후 벡터 삭제
- 관리자 메타데이터(카테고리·짧은 설명·키워드) 수정 시 벡터 재업서트
- 벌크 등록(부분 성공 정책), 연령/성별/검수 상태 일괄 변경, 키워드 일괄 추가·수정, 키워드 통계

### 3. 벡터 검색 API
- `POST /api/search/vector`: 슬롯 키워드 단위 벡터 검색 + 대분류 dominant·키워드 매칭 2차 필터
- `GET /api/admin/products/similarity-search`: 벡터 Top-K 후보 + 조건 2차 필터 + 페이징
- `GET /admin/vector/debug`: Qdrant raw score 확인용

### 4. 키워드 기반 추천 (v1)
- `product` 테이블에서 키워드·가격으로 조회하고, 부족하면 네이버 쇼핑 API로 가져와 저장

### 5. 외부 API 쿼터 관리
- **Redis Lua Script 기반 원자적 Rate Limiter** (초당 9건, 일간 25,000건) — 네이버 API 호출 전 획득

### 6. 기타
- Caffeine 임베딩 캐시 (최대 50,000건, 12시간 TTL)
- 요청/응답 로그 이벤트 발행 → `@Async` 리스너가 DB에 저장
- 클라이언트가 생성한 AI 질문·답변 저장 API
- GPT-4.1-mini 기반 상품 짧은 설명·카테고리 생성 컴포넌트 (`ShortDescriptionGenerator`, `CategoryGenerator`) — **현재 호출 경로 없음** (백필 잡 미포함)

## 알려진 문제

v2에서 측정 후 수정합니다. 근거: [`docs/v2/00-setup.md`](docs/v2/00-setup.md)

- **벡터 검색 점수 해석 오류**: Qdrant Cosine `score`(유사도)를 거리로 보고 `1 - score`로 변환한 뒤 임계값을 적용 (`QdrantVectorProductSearch`)
- **연령 필터로 벡터 결과 0건**: Qdrant payload에 `age`/`gender`가 없는데 추천 요청의 연령(예: `"20대"`)을 match 필터로 전달
- **네이버 쇼핑 검색 API 종료 (2026-07-31)**: 외부 보충 경로와 키워드 기반 추천의 네이버 보충이 항상 실패 (`shop.json` 404)
- 상품 저장 시 임베딩 업서트가 두 번 실행되고, 임베딩 실패 시 재시도가 중첩됨 (`Thread.sleep`으로 요청 스레드 블로킹)
- 벡터 계층(`vector/`)과 `RecommendationVectorService` 테스트 없음

## 로드맵 (v2)

- [x] 로컬 환경·평가용 데이터 구축 ([00-setup](docs/v2/00-setup.md))
- [x] README를 실제 구현 기준으로 정정
- [ ] 추천 품질 평가 하네스 및 기준선 측정 (Hit@8, Recall@8, MRR, 출처별 비율, p50/p95)
- [ ] 코사인 score 해석 오류 수정 + Testcontainers 기반 벡터 검색 테스트 + 임계값 재선정
- [ ] Spring Boot 업그레이드
- [ ] Spring AI 도입 (`EmbeddingModel`, Qdrant `VectorStore`) 및 기존 구현과 비교
- [ ] 연령/성별 필터 정합성 (payload 필드 추가 또는 필터 제거)
- [ ] 관측성 지표 (벡터 검색·임베딩 Timer, 캐시 적중률, 추천 출처별 카운터)
- [ ] 외부 상품 소스 교체 또는 비활성화
- [ ] RAG 기반 추천 이유 생성
- [ ] Redis 임베딩 캐시, Resilience4j 재시도·fallback
- [ ] 추천 서비스 분리 (후보 수집·재분배·보충)

## 프로젝트 구조

```
src/main/java/com/example/giftrecommender/
├── config/          # Spring 설정 (Async, OpenAI, Qdrant, Swagger, Redis 등)
├── controller/      # REST API 엔드포인트 (10개)
├── domain/
│   ├── entity/      # JPA 엔티티 (CrawlingProduct, Product, Guest, Session, Question 등)
│   ├── enums/       # 열거형 (Gender, Age, SessionStatus 등)
│   └── repository/  # Spring Data JPA + Querydsl 리포지토리
├── dto/             # 요청/응답 DTO
├── mapper/          # 엔티티 ↔ DTO 변환
├── service/         # 비즈니스 로직 (추천, 상품 관리, 메타데이터 등)
├── vector/          # 임베딩, Qdrant 검색/업서트, 벡터 동기화 이벤트 리스너
├── infra/naver/     # 네이버 쇼핑 API 클라이언트
├── common/
│   ├── exception/   # 전역 예외 처리
│   ├── logging/     # 요청/응답 로깅 (Filter, Interceptor, 비동기 저장)
│   └── quota/       # Redis Lua Script Rate Limiter
├── util/            # 추천 유틸 (Jaccard 유사도, 상품명 정리 등)
└── init/            # Qdrant 컬렉션 초기화
scripts/seed/        # 로컬 평가용 시드 데이터 수집 스크립트
```

## API 엔드포인트

### 사용자

| Method | Endpoint | 설명 |
|--------|----------|------|
| `POST` | `/api/guests` | 게스트 등록 |
| `POST` | `/api/guests/{guestId}/recommendation-sessions` | 추천 세션 생성 |
| `GET` | `/api/questions` | 설문 질문 조회 |
| `POST` | `/api/guests/{guestId}/recommendation-sessions/{sessionId}/answers` | 설문 답변 제출 |
| `POST` | `/api/guests/{guestId}/recommendation-sessions/{sessionId}/ai-answers` | AI 질문·답변 저장 |
| `POST` | `/api/guests/{guestId}/recommendation-sessions/{sessionId}/recommendation` | 키워드 기반 추천 |
| `GET` | `/api/guests/{guestId}/recommendation-sessions/{sessionId}/recommendation` | 추천 결과 조회 |
| `POST` | `/api/guests/{guestId}/recommendation-sessions/{sessionId}/recommendation/vector` | 벡터 기반 추천 |
| `POST` | `/api/search/vector` | 슬롯 키워드 벡터 검색 |

### 관리자

| Method | Endpoint | 설명 |
|--------|----------|------|
| `POST` | `/api/admin/products`, `/api/admin/products/bulk` | 상품 등록 / 벌크 등록 |
| `GET` | `/api/admin/products` | 상품 목록 (동적 검색) |
| `GET` | `/api/admin/products/similarity-search` | 벡터 유사도 기반 상품 검색 |
| `GET`, `PATCH`, `DELETE` | `/api/admin/products/{product_id}` | 상품 조회 / 수정 / 삭제 |
| `GET`, `PUT` | `/api/admin/products/{productId}/metadata` | 메타데이터 조회 / 수정(벡터 재업서트) |
| `PUT` | `/api/admin/products/{product_id}/age`, `/gender`, `/confirm` (+ `/bulk`) | 연령 / 성별 / 검수 상태 변경 |
| `POST` | `/api/admin/products/{product_id}/score` | 관리자 점수 부여 (관리자 확인 처리) |
| `POST` | `/api/admin/products/keywords/bulk-add`, `/bulk-update` | 키워드 일괄 추가 / 수정 |
| `GET` | `/api/admin/products/stats/keyword` | 키워드 통계 |
| `GET` | `/admin/vector/debug` | Qdrant raw score 디버그 |

> 벡터 관련 API는 `vector.enabled=true`일 때만 활성화됩니다. 전체 API 문서는 Swagger UI (`/swagger-ui/index.html`)에서 확인할 수 있습니다.

## 실행 방법 (Windows / PowerShell)

### 사전 요구사항
- Java 21, Docker
- MySQL 8.x (`gift_recommendation`, 테스트용 `gift_recommendation_test`)
- Redis

### 1. Qdrant 실행
```powershell
docker compose -f docker-compose-local.yml up -d qdrant
```

### 2. 환경 설정
`src/main/resources/application-secret.yml`(gitignore 대상)에 프로파일별 문서로 설정합니다:
```yaml
spring:
  config:
    activate:
      on-profile: local
  datasource:
    password: {DB_PASSWORD}
  redis:
    host: localhost
    port: 6379

naver:
  client-id: {NAVER_CLIENT_ID}
  client-secret: {NAVER_CLIENT_SECRET}

openai:
  api:
    key: {OPENAI_API_KEY}
---
# test, prod 프로파일도 같은 형식으로 추가
```

### 3. 애플리케이션 실행
```powershell
.\gradlew.bat bootRun --args=--spring.profiles.active=local
```

### 4. (선택) 로컬 평가용 상품 데이터 적재
```powershell
.\scripts\seed\seed-10x10.ps1
```
텐바이텐 공개 상품 페이지에서 로컬 평가용으로만 수집합니다. 수집 데이터는 저장소에 포함하지 않습니다. 자세한 내용은 [`docs/v2/00-setup.md`](docs/v2/00-setup.md)를 참고하세요.

### 5. 테스트
```powershell
.\gradlew.bat test
```

## CI/CD

- **CI**: `main`, `dev` 대상 PR 생성 시 GitHub Actions로 빌드 및 테스트 (MySQL·Redis 서비스 컨테이너)
- **CD**: EC2 배포 워크플로 (현재 비활성화)
