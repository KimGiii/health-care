# Gainsy 문서 인덱스

이 저장소의 **모든 문서를 한눈에 찾는 카탈로그**입니다. 카테고리별로 정리되어 있으며, 각 문서에는 한 줄 설명을 달았습니다.

> 새 문서를 추가하면 이 인덱스에도 한 줄을 추가합니다. 디렉토리 구조와 명명 규칙은 맨 아래 [문서 관리 규칙](#문서-관리-규칙)을 참고하세요.

---

## 🚀 어디서부터 읽을까

| 상황 | 읽는 순서 |
| --- | --- |
| **프로젝트 처음 파악** | [README](../README.md) → [ARCHITECTURE](../ARCHITECTURE.md) → [CONTEXT-MAP](../CONTEXT-MAP.md) |
| **백엔드 작업 시작** | [CONTEXT-MAP](../CONTEXT-MAP.md) → [backend/CONTEXT](../backend/CONTEXT.md) → [Backend ADR](../backend/docs/adr/README.md) |
| **iOS 작업 시작** | [CONTEXT-MAP](../CONTEXT-MAP.md) → [ios/CONTEXT](../ios/CONTEXT.md) → [iOS ADR](../ios/docs/adr/README.md) |
| **식단 추천 작업** | [project-memory](agents/project-memory.md) → [추천 제약 PRD](product-specs/DIET_RECOMMENDATION_RESTRICTIONS_PRD.md) → [ADR 0005](adr/0005-versioned-allergen-evidence-fail-closed.md) → [ADR 0002](adr/0002-goal-aware-nutrition-optimization.md) → [알러지 강화 계획](exec-plans/DIET_ALLERGEN_VERIFIED_ONLY_HARDENING.md) → [추천 최적화 계획](exec-plans/DIET_RECOMMENDATION_OPTIMIZATION.md) |
| **현재 진행 상황·지표** | [정량 변화 지표](product-specs/GAINSY_QUANTIFIED_PROGRESS.md) |

---

## 🧭 시작점 · 최상위 문서

| 문서 | 내용 |
| --- | --- |
| [README](../README.md) | 프로젝트 개요, 기능, 기술 스택, 보안·품질 요약 |
| [ARCHITECTURE](../ARCHITECTURE.md) | 시스템 설계 문서 — 구조·기술 결정 이유 |
| [CONTEXT-MAP](../CONTEXT-MAP.md) | 에이전트용 도메인 문서 지도 — 작업 전 읽는 순서 |
| [AGENTS](../AGENTS.md) | 프로젝트 기획 개요 |

## 📖 컨텍스트 · 용어집

| 문서 | 내용 |
| --- | --- |
| [backend/CONTEXT](../backend/CONTEXT.md) | 백엔드 도메인 용어집 (canonical terms) |
| [ios/CONTEXT](../ios/CONTEXT.md) | iOS 도메인 용어집 |

## 🏛️ ADR — 아키텍처 결정 기록

| 인덱스 | 범위 |
| --- | --- |
| [공통 ADR](adr/README.md) | 백엔드/iOS 경계를 넘는 시스템 전반 결정 |
| [Backend ADR](../backend/docs/adr/README.md) | 백엔드 모듈 경계·설계 결정 |
| [iOS ADR](../ios/docs/adr/README.md) | iOS 앱 설계 결정 |

**공통 ADR 목록**
- [0001 — 식단 추천 알러젠 회피 모델과 Strict 모드](adr/0001-diet-allergen-strict-mode.md) — ADR 0005로 대체
- [0002 — 검증 후보로 목표별 남은 영양량 제약 최적화](adr/0002-goal-aware-nutrition-optimization.md)
- [0003 — 식단 추천 제약 최적화를 순수 Java 결정적 탐색으로 구현](adr/0003-constraint-recommendation-engine.md)
- [0004 — 완성요리(복합 식품)를 추천 후보로 도입](adr/0004-composite-dish-recommendation.md)
- [0005 — 알러지 추천은 버전된 근거와 fail-closed 완결 프로필로 판정](adr/0005-versioned-allergen-evidence-fail-closed.md)

**Backend ADR 목록**
- [0001 — 식단 기록 규칙을 유스케이스 모듈에 둔다](../backend/docs/adr/0001-diet-log-use-case-module.md)
- [0002 — 식단 추천 후보 정책을 후보 풀 모듈에 둔다](../backend/docs/adr/0002-diet-recommendation-candidate-pool-module.md)
- [0003 — 추천 큐레이션 불변 조건을 값 객체 모듈에 둔다](../backend/docs/adr/0003-recommendation-curation-module.md)
- [0004 — 식단 추천 엔진은 후보 값 객체만 입력으로 받는다](../backend/docs/adr/0004-diet-recommendation-engine-candidate-interface.md)

## 📐 제품 기획 · 설계

### 설계 문서 (`design-docs/`)
| 문서 | 내용 |
| --- | --- |
| [PRD](design-docs/PRD.md) | 제품 요구사항 정의서 |
| [API_DESIGN](design-docs/API_DESIGN.md) | REST API 엔드포인트 명세 |
| [DB_SCHEMA](design-docs/DB_SCHEMA.md) | PostgreSQL 테이블 설계 |
| [MVP_SCREEN_STRUCTURE](design-docs/MVP_SCREEN_STRUCTURE.md) | MVP 화면 구조·네비게이션 흐름 |
| [USER_SCENARIOS_WIREFRAMES](design-docs/USER_SCENARIOS_WIREFRAMES.md) | 사용자 시나리오 / 와이어프레임 |
| [EXERCISE_EXTERNAL_INTEGRATION](design-docs/EXERCISE_EXTERNAL_INTEGRATION.md) | 운동 외부 데이터 연동 설계 |

### 제품 스펙 (`product-specs/`)
| 문서 | 내용 |
| --- | --- |
| [GAINSY_QUANTIFIED_PROGRESS](product-specs/GAINSY_QUANTIFIED_PROGRESS.md) | **정량 변화 지표 기준 문서** — 구현 규모·운영·품질 수치 |
| [DIET_RECOMMENDATION_RESTRICTIONS_PRD](product-specs/DIET_RECOMMENDATION_RESTRICTIONS_PRD.md) | 알러지·기피 식품 회피 기반 목표 식단 추천 기획서 |

## 🛠️ 실행 계획 (`exec-plans/`)

### 식단 추천
| 문서 | 내용 |
| --- | --- |
| [DIET_ALLERGEN_VERIFIED_ONLY_HARDENING](exec-plans/DIET_ALLERGEN_VERIFIED_ONLY_HARDENING.md) | 버전된 알러젠 근거·완결 프로필 기반 fail-closed 전환 계획 |
| [DIET_RECOMMENDATION_OPTIMIZATION](exec-plans/DIET_RECOMMENDATION_OPTIMIZATION.md) | 목표별 남은 영양량 식단 추천 최적화 실행 계획 |
| [DIET_RECOMMENDATION_RESTRICTIONS](exec-plans/DIET_RECOMMENDATION_RESTRICTIONS.md) | 제외 식품·알러지 기반 하루 식단 추천 실행 계획 |
| [DIET_RECOMMENDATION_ETM_ENHANCEMENTS](exec-plans/DIET_RECOMMENDATION_ETM_ENHANCEMENTS.md) | ETM 관찰 기반 식단 추천 보강 설계 |

### 식품 카탈로그 · AI
| 문서 | 내용 |
| --- | --- |
| [FOOD_CATALOG_ENRICHMENT](exec-plans/FOOD_CATALOG_ENRICHMENT.md) | 식품 데이터(food_catalog) 보강 실행 계획 |
| [MEAL_PHOTO_AI_ANALYSIS_QUALITY](exec-plans/MEAL_PHOTO_AI_ANALYSIS_QUALITY.md) | 사진 기반 AI 식단 분석 품질 개선 실행 계획 |

### iOS 위젯
| 문서 | 내용 |
| --- | --- |
| [IOS_WIDGET_PLAN](exec-plans/IOS_WIDGET_PLAN.md) | iOS 위젯(WidgetKit) 도입 계획 |
| [IOS_WIDGET_PHASE1_SETUP](exec-plans/IOS_WIDGET_PHASE1_SETUP.md) | Phase 1 — Widget Extension 타겟 추가 가이드 |
| [IOS_WIDGET_MULTI_GOAL_PLAN](exec-plans/IOS_WIDGET_MULTI_GOAL_PLAN.md) | 다중 활성 목표 처리 계획 (목표 위젯) |

### App Store 출시·심사
| 문서 | 내용 |
| --- | --- |
| [APPSTORE_RELEASE_CHECKLIST](exec-plans/APPSTORE_RELEASE_CHECKLIST.md) | App Store 출시 준비 체크리스트 |
| [APPSTORE_PRIVACY_LABELS](exec-plans/APPSTORE_PRIVACY_LABELS.md) | Privacy Nutrition Labels 입력 가이드 |
| [APPSTORE_REVIEW_REJECTION_2026_05_15](exec-plans/APPSTORE_REVIEW_REJECTION_2026_05_15.md) | 심사 거절 대응 — Guideline 2.1(a) |
| [APPSTORE_REVIEW_REJECTION_2026_05_19](exec-plans/APPSTORE_REVIEW_REJECTION_2026_05_19.md) | 재심사 거절 대응 — 2026-05-19 (3건) |

### 로드맵 · TODO · 수익화
| 문서 | 내용 |
| --- | --- |
| [MVP_ROADMAP](exec-plans/MVP_ROADMAP.md) | 서비스 MVP 개발 로드맵 |
| [BACKEND_TODO](exec-plans/BACKEND_TODO.md) | 백엔드 TODO |
| [IOS_TODO](exec-plans/IOS_TODO.md) | iOS TODO |
| [BACKEND_IOS_SYNC_WORKFLOW](exec-plans/BACKEND_IOS_SYNC_WORKFLOW.md) | 백엔드-iOS 연동 작업 흐름 |
| [ADS_MONETIZATION](exec-plans/ADS_MONETIZATION.md) | 광고 수익화 전략 및 구현 가이드 |

## ⚙️ 운영 (`operations/`)

| 문서 | 내용 |
| --- | --- |
| [COVERAGE_MEASUREMENT](operations/COVERAGE_MEASUREMENT.md) | 테스트 커버리지 측정 절차 — Jacoco, Xcode xccov |
| [DIET_RECOMMENDATION_CURATION_BATCH_RUNBOOK](operations/DIET_RECOMMENDATION_CURATION_BATCH_RUNBOOK.md) | 추천 후보 큐레이션 배치 운영 절차 |
| [MONITORING_PROMETHEUS_GRAFANA](operations/MONITORING_PROMETHEUS_GRAFANA.md) | 모니터링 구축 가이드 — Prometheus + Grafana |
| [DOMAIN_MIGRATION_GAINSY_SITE](operations/DOMAIN_MIGRATION_GAINSY_SITE.md) | 도메인 전환 가이드 — api.gainsy.site (HTTPS) |
| [TROUBLESHOOTING](operations/TROUBLESHOOTING.md) | 트러블슈팅 로그 |

## 🔬 아키텍처 리뷰 (`architecture-reviews/`)

`/improve-codebase-architecture` 스킬이 생성하는 HTML 리포트 영구 보관 위치. 파일명 규칙: `phase<N>-<topic>.html`.

| 리포트 | |
| --- | --- |
| [food-catalog-allergen-recommendation-review-2026-06-15](architecture-reviews/food-catalog-allergen-recommendation-review-2026-06-15.md) | 식품 카탈로그·알러젠 추천 리뷰 (md) |
| [phase1-recommendation-policy-benchmark](architecture-reviews/phase1-recommendation-policy-benchmark.html) | 추천 정책 벤치마크 |
| [phase2-allergen-gate](architecture-reviews/phase2-allergen-gate.html) | 알러젠 게이트 |
| [phase2-diet-allergen-review](architecture-reviews/phase2-diet-allergen-review.html) | 식단 알러젠 리뷰 |
| [phase2-food-catalog](architecture-reviews/phase2-food-catalog.html) | 식품 카탈로그 |
| [phase3-brand-csv-importer](architecture-reviews/phase3-brand-csv-importer.html) | 브랜드 CSV 임포터 |
| [phase4-recommendation-curation](architecture-reviews/phase4-recommendation-curation.html) | 추천 큐레이션 |
| [phase4-recommendation-scoring-depth](architecture-reviews/phase4-recommendation-scoring-depth.html) | 추천 스코어링 심화 |
| [phase5-external-api-removal](architecture-reviews/phase5-external-api-removal.html) | 외부 API 제거 |
| [phase5-search-record-path](architecture-reviews/phase5-search-record-path.html) | 검색-기록 경로 |
| [allergen-pipeline-widget-metrics-2026-06-17](architecture-reviews/allergen-pipeline-widget-metrics-2026-06-17.html) | 알러젠 파이프라인·위젯 지표 |

## 📚 레퍼런스 · 조사 (`references/`)

수집·조사·검증 근거 문서. 일부는 원천 데이터(CSV·JSON·HTML·PNG)를 같은 디렉토리에 함께 보관합니다.

### 시장·API 조사
| 문서 | 내용 |
| --- | --- |
| [KOREA_HEALTH_FITNESS_MARKET_2026](references/KOREA_HEALTH_FITNESS_MARKET_2026.md) | 2026 국내 헬스·피트니스 앱 시장 조사 |
| [EXERCISE_API_SURVEY_2026-04-22](references/EXERCISE_API_SURVEY_2026-04-22.md) | 운동 종목·칼로리 API 조사 |
| [FATSECRET_PLATFORM_API_TERMS_2026-06-09](references/FATSECRET_PLATFORM_API_TERMS_2026-06-09.md) | FatSecret Platform API 약관 검토 |
| [EAT_THIS_MUCH_RECOMMENDATION_TEARDOWN_2026-06-20](references/EAT_THIS_MUCH_RECOMMENDATION_TEARDOWN_2026-06-20.md) | Eat This Much 공개 동작 관찰 기반 추천 벤치마킹 |

### 식품 카탈로그 데이터
| 문서 | 내용 |
| --- | --- |
| [FOOD_CATALOG_DATA_PROFILING_2026-06-09](references/FOOD_CATALOG_DATA_PROFILING_2026-06-09.md) | 식품 카탈로그 데이터 프로파일링 |
| [FOOD_CATALOG_DATA_REUSE_AND_STAGING_VERIFICATION_2026-06-18](references/FOOD_CATALOG_DATA_REUSE_AND_STAGING_VERIFICATION_2026-06-18.md) | 데이터 재사용·staging 검증 근거 |
| [DIET_RECOMMENDATION_GREEDY_BASELINE_2026-06-18](references/DIET_RECOMMENDATION_GREEDY_BASELINE_2026-06-18.md) | 현행 greedy 추천 benchmark baseline |

### 알러젠 · 브랜드 메뉴 영양/알레르기
| 문서 | 내용 |
| --- | --- |
| [ALLERGEN_SEED_COVERAGE_2026-06-16](references/ALLERGEN_SEED_COVERAGE_2026-06-16.md) | 알러젠 seed 커버리지 리포트 |
| [BRAND_ALLERGEN_CSV_VERIFICATION_2026-06-17](references/BRAND_ALLERGEN_CSV_VERIFICATION_2026-06-17.md) | 브랜드 공식 메뉴 알러젠 CSV 검수 |
| [BURGER_KING_KOREA_NUTRITION_ALLERGY_2026-06-09](references/BURGER_KING_KOREA_NUTRITION_ALLERGY_2026-06-09.md) | 버거킹 코리아 영양·알레르기 |
| [MCDONALDS_KOREA_NUTRITION_ALLERGY_2026-06-09](references/MCDONALDS_KOREA_NUTRITION_ALLERGY_2026-06-09.md) | 맥도날드 영양·알레르기 |
| [SUBWAY_KOREA_NUTRITION_ALLERGY_2026-06-09](references/SUBWAY_KOREA_NUTRITION_ALLERGY_2026-06-09.md) | 써브웨이 코리아 영양·알레르기 |
| [LOTTERIA_KOREA_DELIVERY_NUTRITION_ALLERGY_2026-06-08](references/LOTTERIA_KOREA_DELIVERY_NUTRITION_ALLERGY_2026-06-08.md) | 롯데리아 배달 영양·알레르기 |
| [KRISPY_KREME_KOREA_NUTRITION_ALLERGY_2026-05-04](references/KRISPY_KREME_KOREA_NUTRITION_ALLERGY_2026-05-04.md) | 크리스피크림 영양·알러지 |

> 원천 데이터(`*.csv`, `*.json`, `*.html`, `*.png`, `*.txt`)는 위 리포트와 동일한 `references/` 디렉토리에 날짜 접미사로 함께 보관됩니다.

## 🔁 회고 (`retrospectives/`)

| 문서 | 내용 |
| --- | --- |
| [2026-05-21-backend-code-review](retrospectives/2026-05-21-backend-code-review.md) | 백엔드 코드 리뷰 회고 (보안·트랜잭션) |
| [2026-W22](retrospectives/2026-W22.md) | 주간 회고 — W22 |
| [2026-W21](retrospectives/2026-W21.md) | 주간 회고 — W21 |
| [2026-W20](retrospectives/2026-W20.md) | 주간 회고 — W20 |
| [2026-W19](retrospectives/2026-W19.md) | 주간 회고 — W19 |
| [2026-W18](retrospectives/2026-W18.md) | 주간 회고 — W18 |
| [2026-W17](retrospectives/2026-W17.md) | 주간 회고 — W17 |
| [2026-W16](retrospectives/2026-W16.md) | 주간 회고 — W16 |
| [2026-W15](retrospectives/2026-W15.md) | 주간 회고 — W15 |

## 🤖 에이전트 운영 (`agents/`)

| 문서 | 내용 |
| --- | --- |
| [domain](agents/domain.md) | 도메인 문서 운영 규칙 |
| [project-memory](agents/project-memory.md) | 세션 간 유지하는 확정 제품 결정·우선순위 |
| [issue-tracker](agents/issue-tracker.md) | GitHub Issues 운영 (KimGiii/Gainsy) |
| [triage-labels](agents/triage-labels.md) | 트리아지 라벨 규칙 |

## ✍️ 카피 · 디자인 · 포트폴리오

| 문서 | 내용 |
| --- | --- |
| [COPY](COPY.md) | 앱 카피(문구) 가이드 |
| [APPSTORE_RELEASE_NOTES](APPSTORE_RELEASE_NOTES.md) | App Store 업데이트 문구 가이드 |
| [DESIGN-REGRESSION-CHECKLIST](DESIGN-REGRESSION-CHECKLIST.md) | 디자인 시스템 비주얼 회귀 점검 체크리스트 |
| [FOOD_CATALOG_GUIDE](FOOD_CATALOG_GUIDE.md) | 식품 카탈로그 기술 가이드 |
| [PORTFOLIO](PORTFOLIO.md) | Gainsy 포트폴리오 (소스: `portfolio/`) |

## 🧱 백엔드 제안 · 인프라

| 문서 | 내용 |
| --- | --- |
| [AI_FOOD_SEARCH_PROPOSAL](../backend/docs/AI_FOOD_SEARCH_PROPOSAL.md) | AI 기반 식품 검색 시스템 제안서 |
| [infra/iam/README](../infra/iam/README.md) | IAM 정책 |
| [infra/terraform/aws/README](../infra/terraform/aws/README.md) | AWS Terraform |
| [research/RESEARCH_REPORT](../research/RESEARCH_REPORT.md) | Health Tracking App 종합 리서치 리포트 |

---

## 문서 관리 규칙

| 디렉토리 | 용도 | 명명 규칙 |
| --- | --- | --- |
| `docs/design-docs/` | 변하지 않는 제품·기술 설계 | `UPPER_SNAKE_CASE.md` |
| `docs/product-specs/` | 제품 스펙·정량 지표 기준 문서 | `UPPER_SNAKE_CASE.md` |
| `docs/exec-plans/` | 실행 계획·TODO·체크리스트 | `UPPER_SNAKE_CASE.md` |
| `docs/operations/` | 운영·인프라 가이드 | `UPPER_SNAKE_CASE.md` |
| `docs/adr/` | 시스템 전반 아키텍처 결정 | `NNNN-kebab-title.md` |
| `docs/architecture-reviews/` | 아키텍처 리뷰 리포트 | `phase<N>-<topic>.html` |
| `docs/references/` | 조사·검증 근거 + 원천 데이터 | `TOPIC_YYYY-MM-DD.md` |
| `docs/retrospectives/` | 코드 리뷰·주간 회고 | `YYYY-Wnn.md` |
| `docs/agents/` | 에이전트 운영 규칙 | `kebab-case.md` |

**규칙**
- 새 문서를 추가하면 이 인덱스(`docs/README.md`)에 한 줄을 추가합니다.
- 실제 아키텍처 결정은 채팅에만 남기지 말고 해당 `adr/`에 ADR로 기록합니다.
- 측정 가능한 진척이 바뀌면 [GAINSY_QUANTIFIED_PROGRESS](product-specs/GAINSY_QUANTIFIED_PROGRESS.md)를 함께 갱신합니다.
- 도메인 용어는 작업 전 [backend/CONTEXT](../backend/CONTEXT.md) · [ios/CONTEXT](../ios/CONTEXT.md)의 canonical term을 따릅니다.
