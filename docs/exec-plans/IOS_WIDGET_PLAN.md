# iOS 위젯 (WidgetKit) 도입 계획

> 작성일: 2026-06-05
> 대상: iOS 앱 (HealthCare / Gainsy)
> 기술: WidgetKit, SwiftUI, App Group

---

## 1. 배경 및 목표

### 현황
- 현재 앱에 위젯 관련 코드 **전무**. 완전 신규 개발.
- 홈 화면(`Features/Home/`)에서 오늘 칼로리·매크로·목표 진행률 데이터를 이미 다루고 있어, 위젯 데이터 소스로 재활용 가능.
- 잠금화면 / 홈 화면에서 한눈에 핵심 지표를 보여줘 **앱 재방문율과 기록 빈도**를 끌어올리는 것이 핵심 목적.

### 성공 기준
- 위젯 추가율: 출시 후 30일 내 활성 사용자 중 **15%+** 가 홈/잠금화면에 위젯 추가.
- 위젯 보유 사용자의 일평균 기록 횟수가 비보유 사용자 대비 **+20%**.
- 위젯 갱신 지연 ≤ 1분 (앱 내 기록 직후).

---

## 2. 위젯 종류 (총 3종)

### 2.1 칼로리 위젯 (1순위)

| 크기 | 표시 내용 |
|------|----------|
| Small | 오늘 섭취 칼로리 / 목표 칼로리 + 진행 링 |
| Medium | 칼로리 + 단백질 / 탄수화물 / 지방 3줄 프로그레스 |

- 데이터 소스: 홈 뷰의 `DailyNutritionSummary`
- 딥링크: 탭 시 다이어리(식단 기록) 화면으로 이동

### 2.2 목표 진행률 위젯 (2순위)

| 크기 | 표시 내용 |
|------|----------|
| Small | 현재 활성 목표 이름 + 달성률 % |
| Medium | 목표 + 최근 7일 체중/체지방 미니 차트 |

- 데이터 소스: `GoalSetting` 활성 목표 + `BodyMeasurement` 최근 7일
- 딥링크: 목표 상세 화면

### 2.3 스트릭 위젯 (3순위, 잠금화면 포함)

| 크기 | 표시 내용 |
|------|----------|
| Accessory Circular | 연속 기록 일수 (잠금화면) |
| Accessory Rectangular | 스트릭 + 오늘 운동 여부 (잠금화면) |
| Small | 스트릭 + 오늘 식단/운동 기록 체크 |

- 데이터 소스: 백엔드 신규 API `GET /api/streak` 또는 클라이언트 계산
- 딥링크: 홈 화면

---

## 3. 기술 아키텍처

### 3.1 타겟 구조

```
ios/
├── HealthCare/                  # 기존 메인 앱
│   └── ...
├── HealthCareWidget/            # 신규 Widget Extension 타겟
│   ├── HealthCareWidgetBundle.swift   # @main, WidgetBundle
│   ├── Calorie/
│   │   ├── CalorieEntry.swift         # TimelineEntry
│   │   ├── CalorieProvider.swift      # TimelineProvider
│   │   └── CalorieWidgetView.swift
│   ├── Goal/
│   │   ├── GoalEntry.swift
│   │   ├── GoalProvider.swift
│   │   └── GoalWidgetView.swift
│   ├── Streak/
│   │   ├── StreakEntry.swift
│   │   ├── StreakProvider.swift
│   │   └── StreakWidgetView.swift
│   └── Info.plist
└── HealthCareShared/            # 신규 공유 Framework / Folder
    ├── WidgetDataStore.swift          # App Group I/O
    ├── WidgetSnapshot.swift           # Codable 스냅샷 모델
    └── WidgetReloadCenter.swift       # 갱신 트리거 래퍼
```

> 참고: `HealthCareShared`를 정적 프레임워크가 아닌 **공유 폴더(Compile Sources 양쪽 추가)** 방식으로 시작 → 의존성 단순화. 추후 코드 양 늘면 SPM Local Package로 전환.

### 3.2 데이터 공유 — App Group + UserDefaults

- App Group ID: `group.com.kingloo.gainsy.widgets`
- 저장 키 네이밍: `widget.calorie.snapshot`, `widget.goal.snapshot`, `widget.streak.snapshot`
- 포맷: JSON `Data` (Codable 직렬화)

```swift
struct CalorieSnapshot: Codable {
    let date: Date
    let consumedKcal: Int
    let targetKcal: Int
    let proteinG: Double
    let proteinTargetG: Double
    let carbsG: Double
    let carbsTargetG: Double
    let fatG: Double
    let fatTargetG: Double
    let updatedAt: Date
}
```

### 3.3 갱신 트리거

1. **앱 → 위젯 (Push 형태)**
   - 홈 뷰 로드 완료 시
   - 식단/운동/체중 기록 직후
   - 호출: `WidgetCenter.shared.reloadTimelines(ofKind: "CalorieWidget")`

2. **위젯 자체 타임라인**
   - `getTimeline` 정책: `.after(Date() + 30min)`
   - 자정 경계에서 강제 갱신 (오늘 vs 어제 구분)

3. **오프라인 처리**
   - 위젯은 절대 네트워크 호출하지 않음. 항상 App Group 캐시 사용.
   - 캐시 부재 시 "기록을 시작해보세요" placeholder 표시.

### 3.4 딥링크

- URL 스킴: `gainsy://widget/calorie`, `gainsy://widget/goal`, `gainsy://widget/streak`
- `widgetURL(_:)` 모디파이어로 위젯 탭 시 라우팅
- 메인 앱의 `onOpenURL`에서 처리 → 기존 Navigation Coordinator로 라우팅

---

## 4. 개발 단계 (총 5일 예상)

### Phase 1 — 인프라 세팅 ✅ 완료 (2026-06-08)
- [ ] Xcode에 Widget Extension 타겟 추가 (`HealthCareWidget`)
- [ ] App Group Capability 활성화 (메인 앱 + 위젯 양쪽)
  - `HealthCare.entitlements`에 `com.apple.security.application-groups` 추가
  - `HealthCareWidget.entitlements` 신규 생성
- [ ] `WidgetDataStore` 공유 레이어 작성 + 양쪽 타겟에 추가
- [ ] URL 스킴 등록 (`Info.plist`)
- [ ] 빈 WidgetBundle로 빌드 통과 확인

### Phase 2 — 칼로리 위젯 ✅ 완료 (2026-06-08)
- [ ] `CalorieSnapshot` 모델 + Codec 테스트
- [ ] 홈 ViewModel에서 데이터 로드 후 `WidgetDataStore.save(_:)` 호출 지점 삽입
- [ ] 식단 기록 완료 핸들러에서 동일 저장 + `reloadTimelines` 호출
- [ ] `CalorieProvider` (Timeline / Snapshot / Placeholder)
- [ ] Small / Medium 뷰 구현 (`Color.brand` 포레스트 그린 + 진행 링)
- [ ] 딥링크 라우팅 → 다이어리 화면 진입 검증

### Phase 3 — 목표 진행률 위젯 ✅ 완료 (2026-06-08)
- [x] `GoalWidgetSnapshot` 모델 (활성 목표 + WeightPoint 배열)
- [x] `WidgetDataStore.saveGoal/loadGoal`
- [x] `HomeViewModel.publishGoalWidgetSnapshot()` — dashboard 로드 종료 시 자동 호출
- [x] `HomeDashboardLoading.loadBodyMeasurements` 추가 (배열 반환 — 단일 배열 응답)
- [x] measurement 로드 범위 30일로 확장 + 최근 7개 추출 (매일 측정 안 해도 차트 채워짐)
- [x] Small / Medium 뷰
- [x] Medium: 현재 체중 큰 글씨 + sparkline + 변화량 알약 + 측정 횟수 푸터
- [x] sparkline: monotone 보간, 각 측정점 dot, 마지막 측정점 흰 테두리 강조
- [x] 1개 측정 상태 / 0개 상태 빈 상태 처리
- [x] 딥링크 → 마이페이지 (목표 상세 destination은 Phase 4+에서)

### Phase 4 — 스트릭 위젯 + 잠금화면 (1일)
- [ ] 백엔드: `GET /api/streak` 엔드포인트 검토 (없다면 클라이언트 계산)
- [ ] `StreakSnapshot` 모델
- [ ] Accessory Circular / Rectangular / Small 뷰
- [ ] 잠금화면용 색 처리 (`AccentedRenderingMode`)

### Phase 5 — QA & 출시 준비 (0.5일)
- [ ] 다크모드 / 라이트모드 양쪽 스크린샷
- [ ] iOS 17 / 18 양쪽 빌드 확인 (StandBy 모드 대응 포함)
- [ ] App Store 스크린샷 6.7" / 6.1" 위젯 화면 추가
- [ ] 릴리스 노트 초안 (한국어 + 영문)

---

## 5. 디자인 가이드

- **컬러**: 기존 `Color.brand` (포레스트 그린) + `Color.accent` (민트) 그대로 사용
- **타이포**: SF Pro Rounded 권장 (위젯 가독성)
- **폰트 사이즈**: Small 위젯 메인 수치는 `.title2.weight(.bold)` 이상
- **여백**: `.padding(12)` 기본, 가장자리 4pt는 시스템 안전 영역 확보
- **상태**:
  - 데이터 있음 → 컨텐츠
  - 첫 사용 (캐시 없음) → "기록을 시작해보세요" + 앱 아이콘
  - 자정 직후 (오늘 기록 없음) → 목표만 표시, 진행률 0%

---

## 6. 위험 및 대응

| 위험 | 영향 | 대응 |
|------|------|------|
| App Group 설정 누락 → 데이터 공유 실패 | HIGH | Phase 1 종료 시 양 타겟 모두 캐시 R/W 통합 테스트 |
| 위젯 타임라인 갱신 지연 (시스템 정책) | MEDIUM | 앱 내 기록 직후 `reloadTimelines` 강제 호출, 자동 갱신은 보조 |
| 잠금화면 위젯 색상 자동 틴팅 | MEDIUM | `widgetAccentable()` 사용, 디자인 검수 |
| 백엔드 streak API 부재 | LOW | 클라이언트에서 `ExerciseRecord` + `DietRecord` 날짜 기반 계산 → 추후 서버화 |
| 위젯이 오래된 데이터 표시 | LOW | 스냅샷에 `updatedAt` 포함, 5분 이상이면 "방금 전 / 5분 전" 표시 |
| 다국어 (i18n) — 한국어/영문 | LOW | 기존 `LocaleManager.resolvedLocale` 패턴 재사용, 위젯은 시스템 로케일 사용 |

---

## 7. 후속 로드맵 (이 계획 외)

- **Interactive Widget** (iOS 17+): 식단 빠른 기록 버튼 (App Intent)
- **Live Activity**: 운동 세션 진행 중 잠금화면 표시
- **watchOS Complication**: Apple Watch 연동 (별도 타겟)
- **Smart Stack 우선순위**: 식사 시간대(아침/점심/저녁)에 칼로리 위젯 자동 노출

---

## 8. 체크리스트 (출시 직전)

- [ ] App Group ID가 Apple Developer Portal에 등록되어 있는가
- [ ] 메인 앱 + 위젯 모두 동일 App Group capability
- [ ] Privacy 라벨: 위젯이 신규 데이터 수집 없음 명시
- [ ] 위젯 추가 가이드 (앱 내 온보딩 / 마이페이지)
- [ ] CHANGELOG 및 App Store 릴리스 노트 (한/영)
- [ ] 스크린샷 (Small/Medium/Lock screen 각 1장)

---

## 참고 자료

- [WidgetKit Documentation](https://developer.apple.com/documentation/widgetkit)
- [App Groups — Sharing data between app and extension](https://developer.apple.com/documentation/xcode/configuring-app-groups)
- [WidgetCenter — reloadTimelines](https://developer.apple.com/documentation/widgetkit/widgetcenter)
