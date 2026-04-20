# 프로젝트 현황 — 2026년 4월 16일

## 📊 전체 진행률

```
Phase 0: 환경 구축           ████████████████████ 100%
Phase 1: 인증 & 사용자       ████████████████████ 100%
Phase 2: 운동 기록          ████████████████████ 100%
Phase 3: 식단 기록          ███████████████████░  95% (버그 수정 완료)
Phase 4: 신체 측정 & 진행 사진  ░░░░░░░░░░░░░░░░░░░░   0% ← 다음 작업
Phase 5: 목표 & 인사이트     ░░░░░░░░░░░░░░░░░░░░   0%
Phase 6: MVP 출시 준비       ░░░░░░░░░░░░░░░░░░░░   0%
```

---

## ✅ 최근 완료 (2026-04-16)

### 버그 수정
- [x] iOS JSON 디코딩 에러 (snake_case vs camelCase)
- [x] SF Symbol 누락 에러 (`bowl.fill` → `fork.knife.circle.fill`)
- [x] FoodCategory enum 불일치 (`PROTEIN` vs `PROTEIN_SOURCE`)

### 식단 검색 기능
- [x] 카탈로그 검색 API (백엔드)
- [x] 외부 식품 API 연동 (공공데이터 포털)
- [x] iOS 검색 UI 구현

---

## 🎯 다음 우선순위 작업

### Phase 4: 신체 측정 & 진행 사진 (예상 1-2주)

#### 백엔드 작업
```
□ Measurement 도메인 구현
  □ Entity: Measurement (체중, 골격근량, 체지방률 등)
  □ Repository & Service
  □ Controller: POST /api/v1/measurements
  □ Controller: GET /api/v1/measurements (기간별 조회)

□ 진행 사진 업로드
  □ S3 Presigned URL 생성 API
  □ 사진 메타데이터 저장 (측정일, URL)
  □ Controller: POST /api/v1/measurements/photos
  □ Controller: GET /api/v1/measurements/photos
```

#### iOS 작업
```
□ 신체 측정 기록 화면
  □ MeasurementRecordView 실 구현
  □ 체중/골격근량/체지방률 입력 폼
  □ API 연동 (POST /api/v1/measurements)

□ 진행 사진 업로드
  □ 사진 선택 (PHPickerViewController)
  □ S3 업로드 플로우
  □ 업로드 진행 상태 표시

□ 측정 히스토리 화면
  □ 시간별 그래프 (Charts 라이브러리)
  □ 진행 사진 갤러리
```

---

## 🔮 이후 계획

### Phase 5: 목표 & 인사이트 (예상 2-3주)
```
□ 목표 설정 API (체중 목표, 칼로리 목표 등)
□ 목표 진행률 계산 로직
□ 주간/월간 요약 대시보드
□ 인사이트 알림 (FCM)
```

### Phase 6: MVP 출시 준비 (예상 1-2주)
```
□ E2E 테스트 전체 시나리오
□ 앱 아이콘 & 스플래시 스크린
□ App Store 스크린샷
□ 개인정보 처리방침
□ TestFlight 배포
```

---

## 📝 보류된 작업

### 후순위 작업 (Phase 7 이후)
- [ ] AI 기반 식품 검색 (pgvector + OpenAI Embeddings)
  - 제안서: `backend/docs/AI_FOOD_SEARCH_PROPOSAL.md`
  - PoC 코드: `backend/docs/AI_SEARCH_POC.kt`
  - 예상 기간: 2-4주
  - 우선순위: Low (MVP 이후)

- [ ] Android 앱 개발
  - 예상 기간: 4-6주
  - 우선순위: Post-MVP

- [ ] 소셜 기능 (친구, 챌린지 등)
  - 예상 기간: 3-4주
  - 우선순위: Post-MVP

---

## 🐛 알려진 이슈

### 없음
- 모든 크리티컬 버그 해결 완료

---

## 🚀 권장 다음 단계

### 1️⃣ Phase 4 시작 (신체 측정)
**이유**: 
- Phase 0-3 완료
- MVP 핵심 기능 중 하나
- iOS와 백엔드 병렬 작업 가능

**예상 기간**: 1-2주

### 2️⃣ 작업 순서
```
Week 1:
  Day 1-2: 백엔드 Measurement API 구현
  Day 3-4: iOS 측정 기록 화면
  Day 5:   통합 테스트

Week 2:
  Day 1-2: S3 사진 업로드 구현
  Day 3-4: iOS 사진 업로드 UI
  Day 5:   E2E 테스트
```

---

**마지막 업데이트**: 2026-04-16  
**다음 회고 예정**: 2026-04-20 (W16)
