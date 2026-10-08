# 00. 로컬 환경 구성 및 데이터 현황

> v2 리팩토링 STEP 0 결과. 모든 수치는 실제 실행 결과다.

## 1. 실행 방법 (Windows / PowerShell)

### 사전 조건
- Docker Desktop, JDK 21
- 로컬 MySQL 8 (`localhost:3306`, DB `gift_recommendation` / 테스트용 `gift_recommendation_test`)
- 로컬 Redis (`localhost:6379`)
- `src/main/resources/application-secret.yml` (gitignore 대상, profile별 datasource 비밀번호·OpenAI 키 등)

### Qdrant 기동
```powershell
docker compose -f docker-compose-local.yml up -d qdrant
```
- 컨테이너 `giftrec-qdrant`, 볼륨 `./qdrant_data` (상대 경로라 Windows에서 동작)
- 프로젝트 루트의 `.env`는 `;` 주석을 쓰고 있어 compose가 파싱하지 못한다. 필요하면 `--env-file`로 빈 파일을 지정한다.

### 애플리케이션 기동
```powershell
.\gradlew.bat bootRun --args=--spring.profiles.active=local
```
- 기동 시 `products` 컬렉션이 없으면 생성된다 (1536차원, Cosine).

### 시드 데이터 적재
```powershell
.\scripts\seed\seed-10x10.ps1           # 앱 기동 후 실행
.\scripts\seed\seed-10x10.ps1 -DryRun   # 수집만
```

## 2. 환경 정리 중 발견한 문제

| 문제 | 영향 | 조치 |
|---|---|---|
| main의 local 프로파일 `ddl-auto: create-drop` | 앱 종료 시 로컬 테이블 삭제 | `update`로 변경 (#57) |
| 기존 compose 볼륨 `/var/qdrant/storage` | Windows에서 Docker VM 내부 경로를 가리켜 프로젝트 폴더와 분리됨 (실제로 비어 있었음) | `./qdrant_data`로 변경 (#57) |
| `application-secret.yml` 들여쓰기 오류 | 모든 프로파일에서 YAML 파싱 실패 → 로컬 테스트·기동 불가 | 로컬 파일 수정 (커밋 대상 아님) |
| CI 테스트 DB 접속 불가 | 전 테스트 컨텍스트 로딩 실패 | CI에 MySQL/Redis 서비스 컨테이너 도입 (#57) |
| Qdrant 헬스체크가 이미지에 없는 `wget` 사용 | 항상 unhealthy | bash `/dev/tcp`로 변경 |
| **네이버 쇼핑 검색 API 종료 (2026-07-31)** | `shop.json` 404(SE05). 앱의 외부 보충 경로(`NaverApiClient`)가 항상 실패 | 시드는 다른 소스로 대체. 앱 경로 처리는 v2 로드맵 |

네이버 검색 API 실측 (2026-10-08, 동일 키):

| API | 결과 |
|---|---|
| news, encyc, blog, webkr, image, kin, cafearticle, local | HTTP 200 |
| shop, doc, book | HTTP 404 (SE05) |

## 3. 데이터 현황

### 기존 데이터
- 운영 DB: 유실되어 덤프 불가
- 로컬 MySQL `crawling_product`: 8건 (2026-01-07 적재), 새 Qdrant에는 해당 포인트 없음
- 기존 `./qdrant_storage`: `products` 컬렉션 포인트 0건 (132MB는 사전 할당된 세그먼트/WAL)

### 시드 데이터 소스 선정
| 후보 | 결과 |
|---|---|
| 네이버 쇼핑 검색 API | 2026-07-31 종료, 대체 API 없음 |
| 11번가 OpenAPI | 판매자 회원 전용 |
| 쿠팡 파트너스 API | 제휴 승인 필요, 호출 한도가 작음 |
| AI Hub 상품 데이터셋 | 유통매장(마트) 상품 위주로 선물 도메인과 맞지 않음 |
| **텐바이텐 공개 상품 페이지 (채택)** | 선물·디자인 전문몰. 상품 페이지의 schema.org JSON-LD(Product, BreadcrumbList) 사용 |

수집 원칙
- robots.txt(`User-agent: *`) 허용 경로(`/search/`, `/shopping/`)만 요청, 요청 간 1초 대기
- 이미지는 내려받지 않고 URL만 보관
- 약관상 금지 사항(영리 목적 이용, 제3자 제공)에 해당하지 않도록 **로컬 평가 전용, 비공개**로 사용
- 수집 결과(`build/seed-10x10.json`)와 DB 데이터는 저장소에 커밋하지 않는다

### 시드 결과
수집일 2026-10-08, 키워드 50개(`scripts/seed/keywords.txt`), 키워드당 검색 상위 20건.

| 항목 | 값 |
|---|---|
| HTTP 요청 수 (검색 + 상품 페이지) | 1,030 |
| 수집 상품 (중복 제거 후) | 980 |
| JSON-LD 파싱 실패 | 0 |
| `/bulk` 적재 성공 / 중복 / 실패 | 980 / 0 / 0 |
| MySQL `crawling_product` 합계 (기존 8건 포함) | 988 |
| Qdrant `products` 포인트 | 988 (MySQL과 일치) |
| 대분류 수 | 20 (텐바이텐 분류 16 + 기존 8건의 분류 4) |
| 가격 범위 / 평균 | 180원 ~ 374,226원 / 31,407원 |

대분류 분포 상위: 디지털/핸드폰 135, 데코/조명 112, 키친 110, 토이/취미 110, 디자인문구 81, 패션잡화 78, 뷰티 73, 주얼리/시계 56, 푸드 51, 패션의류 44.

임베딩 장애와 복구
- 적재 도중 OpenAI 크레딧 소진으로 임베딩이 `429 You have no credits remaining`으로 실패했다. 상품 저장은 성공했고 벡터만 누락됐다.
- 실패한 상품 1건당 임베딩 호출 시도가 약 12회 발생했다: `EmbeddingService` 3회 재시도 × (저장 시 직접 동기화 + `AFTER_COMMIT` 리스너) 각각의 재시도. 재시도해도 소용없는 오류(크레딧 부족)도 구분하지 않고 요청 스레드를 블로킹한다. → 2주차 Resilience4j 작업 근거
- 정상 경로에서도 상품 1건 저장 시 업서트가 두 번 일어난다(직접 동기화 + 리스너, `markEmbedding(..., false)` 때문에 리스너가 건너뛰지 않음).
- 크레딧 충전 후 "MySQL에는 있고 Qdrant에 포인트가 없는" 766건을 관리자 메타데이터 API(`PUT /api/admin/products/{id}/metadata`, 빈 본문)로 재업서트했다: 성공 766 / 실패 0, 138초. 이후 `embedding_ready`를 Qdrant 포인트 존재 여부 기준으로 맞췄다.
- 기존 8건은 `embedding_ready=true`였지만 새 Qdrant에는 포인트가 없었다. 상태 플래그만으로는 DB–벡터 스토어 정합성을 보장하지 못한다.

## 4. 기존 테스트 결과
`.\gradlew.bat test` (2026-10-08, `v2` 브랜치, test 프로파일 → 로컬 `gift_recommendation_test` DB)

| 항목 | 값 |
|---|---|
| 테스트 클래스 / 케이스 | 22 / 42 |
| 성공 / 실패 / 스킵 | 42 / 0 / 0 |
| CI (GitHub Actions, MySQL·Redis 서비스 컨테이너) | 통과 (#55, #56, #57) |

커버리지 공백
- `vector/` 패키지(Qdrant 검색, 임베딩, 업서트 리스너) 테스트 0건. test 프로파일이 `vector.enabled=false`라 벡터 계층 빈 자체가 뜨지 않는다. P0(유사도 해석 오류)가 테스트로 잡히지 않은 원인.
- `RecommendationVectorService`(벡터 기반 추천 진입점) 테스트 0건.
- `NaverApiClientTest`는 통과하지만 실제 네이버 쇼핑 API는 종료된 상태라, 테스트가 외부 API 실제 상태를 반영하지 못한다.
