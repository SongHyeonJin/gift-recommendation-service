# Gift Recommendation Service

AI 기반 맞춤형 선물 추천 서비스

사용자 설문 응답을 분석하여 OpenAI 임베딩 + Qdrant 벡터 유사도 검색으로 최적의 선물을 추천합니다.

## 기술 스택

| 분류 | 기술 |
|------|------|
| **Backend** | Java 21, Spring Boot 3.2.5, Spring Data JPA, Querydsl 5.0 |
| **AI / Vector Search** | OpenAI API (text-embedding-3-small, GPT-4.1-mini), Qdrant Vector DB (gRPC) |
| **Database** | MySQL 8.x, Redis |
| **Infra / DevOps** | Docker, GitHub Actions (CI/CD), AWS EC2 |
| **Docs** | SpringDoc OpenAPI (Swagger UI) |

## 아키텍처

![Sequence Diagram](docs/sequence_diagram.png)

## 주요 기능

### 1. AI 벡터 기반 추천 엔진
- OpenAI `text-embedding-3-small`으로 상품 텍스트를 **1536차원 벡터로 임베딩**
- Qdrant 벡터 DB에서 **코사인 유사도 기반 시맨틱 검색**
- 가격대/연령/성별 **다중 필드 필터링** 및 유사도 임계값 기반 품질 제어
- 키워드 기반 추천 + 벡터 기반 추천 **듀얼 모드** 지원

### 2. LLM 활용 자동 컨텐츠 생성
- GPT 기반 **상품 설명 자동 생성** (`ShortDescriptionGenerator`)
- AI 기반 **자동 카테고리 분류** 및 도메인-축 시맨틱 검증
- AI 생성 설문 질문으로 사용자 맞춤 추천 정확도 향상

### 3. 외부 API 연동 및 Rate Limiting
- **Redis Lua Script 기반 원자적 Rate Limiter** (초당 9건, 일간 25,000건)
- 지수 백오프(Exponential Backoff) 재시도 전략
- Naver Shopping API 연동 (HMAC-SHA256 서명 인증)

### 4. 이벤트 기반 비동기 처리
- `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`로 상품 생성 시 자동 벡터 임베딩
- 이벤트 기반 벡터 삭제 처리
- 비동기 API 요청/응답 로깅 파이프라인

### 5. 다층 캐싱 전략
- **Caffeine Cache**: 임베딩 결과 캐싱 (50,000건, 12시간 TTL)
- **Redis**: 분산 세션 관리 및 쿼터 상태 관리
- Jaccard 유사도 기반 중복 상품 제거

## 프로젝트 구조

```
src/main/java/com/example/giftrecommender/
├── config/          # Spring 설정 (Async, OpenAI, Qdrant, Swagger, Redis 등)
├── controller/      # REST API 엔드포인트 (10개)
├── domain/
│   ├── entity/      # JPA 엔티티 (Product, Guest, Session, Question 등)
│   ├── enums/       # 열거형 (Gender, Age, SessionStatus 등)
│   └── repository/  # Spring Data JPA + Querydsl 리포지토리
├── dto/
│   ├── request/     # 요청 DTO
│   └── response/    # 응답 DTO
├── service/         # 비즈니스 로직 (추천, 크롤링, 카테고리 분류 등)
├── vector/          # 벡터 DB 연동 (임베딩, Qdrant 검색, 이벤트 리스너)
├── infra/
│   ├── naver/       # Naver Shopping API 클라이언트
│   └── redis/       # Redis 쿼터 관리
├── common/
│   ├── exception/   # 전역 예외 처리
│   ├── logging/     # 요청/응답 로깅 (Filter, Interceptor)
│   └── quota/       # Redis Lua Script Rate Limiter
└── init/            # 초기화 작업 (벡터 컬렉션, 카테고리 백필, 스모크 테스트)
```

## API 엔드포인트

| Method | Endpoint | 설명 |
|--------|----------|------|
| `POST` | `/api/guests` | 게스트 등록 |
| `POST` | `/api/recommendations/sessions` | 추천 세션 생성 |
| `POST` | `/api/recommendations/{sessionId}/recommend` | 키워드 기반 추천 |
| `POST` | `/api/recommendations/vector/{sessionId}/recommend` | 벡터 기반 추천 |
| `GET` | `/api/products/similarity-search` | 벡터 유사도 검색 |
| `POST` | `/api/questions` | 설문 질문 CRUD |
| `POST` | `/api/user-answers` | 사용자 답변 제출 |
| `POST` | `/api/user-answers/ai` | AI 생성 질문 기반 답변 |

> 전체 API 문서는 Swagger UI (`/swagger-ui/index.html`)에서 확인할 수 있습니다.

## 실행 방법

### 사전 요구사항
- Java 21
- MySQL 8.x
- Redis
- Docker (Qdrant 실행용)

### 1. Qdrant 실행
```bash
docker-compose -f docker-compose-local.yml up -d
```

### 2. 환경 설정
`src/main/resources/application-secret.yml`에 아래 정보를 설정합니다:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/gift_recommendation
    username: root
    password: {DB_PASSWORD}
  redis:
    host: localhost
    port: 6379

openai:
  api:
    key: {OPENAI_API_KEY}

naver:
  client-id: {NAVER_CLIENT_ID}
  client-secret: {NAVER_CLIENT_SECRET}
```

### 3. 애플리케이션 실행
```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

## CI/CD

- **CI**: PR 생성 시 GitHub Actions로 빌드 및 테스트 자동 실행
- **CD**: main 브랜치 push 시 EC2 자동 배포 (현재 비활성화)
