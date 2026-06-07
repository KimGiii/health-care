# 디자인 시스템 도입 — 비주얼 회귀 점검 체크리스트

오늘(2026-05-26) 토큰 도입으로 화면에 들어간 시각적 변화를 한 곳에 정리.
시뮬레이터/실기기로 화면을 돌면서 의도와 다른 변화가 있는지 확인한다.

회귀 발견 시: 본 문서 §3에 기록 → 토큰 값 조정 또는 화면별 인라인 + `// design-lint:ignore`.

---

## 1. 토큰 변경으로 인한 의도된 비주얼 변화

### 1-1. Radius

| 변경 전 | 변경 후 | 영향 |
|---|---|---|
| cornerRadius 14 | **Radius.lg = 16** | PrimaryButton/SecondaryButton 모서리 2px 더 둥글어짐. Auth/Onboarding/ProfileSetup CTA. |
| cornerRadius 12 | **Radius.md = 12** | StyledTextField/StyledSecureField — 동일 (변화 없음) |

### 1-2. Spacing

| 변경 전 | 변경 후 | 영향 |
|---|---|---|
| padding(.vertical, 14) | **Spacing.lg = 16** | StyledTextField/StyledSecureField 세로 패딩 2px 증가. 입력 필드 살짝 큼. |

### 1-3. Typography

| 화면/요소 | 변경 전 | 변경 후 | 비고 |
|---|---|---|---|
| Login/SignUp 헤더 제목 | 22, bold, **rounded** | **`headingLarge`** (22, bold, system) | design rounded 손실. 좀 더 정형적. |
| ProfileSetup step title | **24**, bold, rounded | **`headingLarge`** (22, bold, system) | 2px 축소 + rounded 손실 |
| 헤더 서브텍스트 (4화면) | 14 regular | **`bodyMedium`** (15 regular) | **1px upscale** |
| Onboarding "Gainsy" 로고 | 32, bold, rounded | **`brandWordmark`** (32, bold, rounded) | 동일 (토큰화만) |
| 폼 라벨 (13/semibold) | 13 semibold | **`labelSmall`** | 동일 |
| 데이터 라벨 (11/medium) | 11 medium | **`captionXSmall`** | 동일 |
| 인라인 14 regular | 14 | **`bodyMedium`** (15) | **1px upscale** (다수 화면) |
| 인라인 26 bold rounded | 26 | **`numeralLarge`** (28 bold rounded) | **2px upscale** (GoalProgress 측정값) |
| 18 bold | 18 bold | **`headingMedium`** (18 semibold) + `.fontWeight(.bold)` | 동일 |
| 16 bold | 16 bold | **`bodyLarge`** (17 regular) + `.fontWeight(.bold)` | **1px upscale** |
| 10 (단독/medium) | 10 | **`captionXSmall`** (11 medium) | **1px upscale** (소수 라벨) |
| 11 단독 | 11 regular | **`captionXSmall`** (11 medium) | weight 살짝 증가 |
| 9 semibold | 9 | 인라인 유지 (`// design-lint:ignore`) | 마이크로 라벨 |

### 1-4. PhotosPicker 격리

`AddProgressPhotoView`의 `PhotoPickerSection`을 별도 `@MainActor` View struct로 분리. 동작 동일, 빌드 통과 위함.

### 1-5. GoalProgressCard 진행률 표시

링 안 숫자가 truncate(`Int`) → 반올림(`%.0f`)로 변경. 같은 카드 안에서 66 vs 67처럼 다르게 보이던 문제 수정.

---

## 2. 화면별 점검 체크리스트

각 항목을 시뮬레이터에서 보고 ☐ → ✅ / 🐛 (회귀) 마킹.

### 2-1. Auth — LoginView
- ☐ "다시 만나서 반가워요" 헤더 — 22 system bold (rounded 아님). 어색하지 않음?
- ☐ "계속하려면 로그인해 주세요" 서브 — 15px (전 14px). 살짝 큼?
- ☐ PrimaryButton "로그인하기" — 모서리 16(전 14). 둥글기 자연스러움?
- ☐ StyledTextField — 세로 패딩 16(전 14). 키보드 노출 시 잘림 없음?
- ☐ 다크모드에서도 위 항목들 정상

### 2-2. Auth — SignUpView
- ☐ "함께 시작해 봐요" 헤더 (띄어쓰기 변경) — 22 system bold
- ☐ 약관 동의 체크박스 (필수) 마이크로 카피
- ☐ PrimaryButton "가입하기" disabled 상태(opacity)

### 2-3. Onboarding — OnboardingView
- ☐ "Gainsy" 로고 32 rounded (변화 없음)
- ☐ 본문 15px 라인스페이싱 4
- ☐ PrimaryButton "로그인하기" + SecondaryButton "계정이 없어요, 가입하기"
- ☐ 배경 데코 블롭 정상 표시

### 2-4. ProfileSetup
- ☐ Step1/Step2 타이틀 22 system bold (전 24 rounded) — 너무 작아 보이지 않음?
- ☐ "신체 정보를 알려주세요" / "평소 활동 수준은?" 굵기/위계 OK?
- ☐ 폼 라벨 "성별" "키 / 몸무게" 13/semibold
- ☐ SexCard 텍스트, ActivityCard 아이콘+텍스트 위계
- ☐ PrimaryButton "다음으로" / "시작하기" disabled 상태

### 2-5. Home — HomeView
- ☐ EmptyMealCard — 이모지 🌱 → SF Symbol "fork.knife" (light weight). 어색하지 않음?
- ☐ EmptyMealCard 라벨 "기록이 아직 없어요" / "첫 식사를 기록해 보세요" 자수 OK?
- ☐ GoalProgressCard 활성 — 진행률 링 % 와 "X% 완료" **두 값 동일** 확인 (66/66 또는 67/67)
- ☐ GoalProgressCard 빈 — "목표가 아직 없어요" / "목표를 세우고 기록을 시작해 보세요"
- ☐ WorkoutCompactCard / MealCard / MacroBreakdownCard 일관성

### 2-6. Diary — DiaryView
- ☐ 날짜 셀(`dayNumber`) — 선택/오늘 상태 weight 변화 자연스러움
- ☐ 식사 카드 — 이모지 size 20, displayName size 9 (마이크로 라벨)
- ☐ 운동/식단/측정 기록 일/주/월 뷰 전환

### 2-7. Record — DietRecordView
- ☐ **EmptyState 컴포넌트 적용** — fork.knife.circle 아이콘 + "오늘 식단 기록이 아직 없어요"
- ☐ CTA "첫 식사 기록하기" — 캡슐 버튼

### 2-8. Record — ExerciseRecordView / AddExerciseSessionView
- ☐ 운동 추가 — 카탈로그 검색
- ☐ **검색 결과 없음** — `magnifyingglass` 아이콘 + "'{검색어}'에 대한 결과가 없어요"
- ☐ AI 운동 추정 결과 표시 (검색 결과 없음 아래)
- ☐ 세트 구성, 칼로리 계산 토글, 저장 버튼

### 2-9. Record — BodyMeasurementView / AddProgressPhotoView / ProgressPhotoView
- ☐ **HeroEmptyState** — 다크 hero 안 "측정 기록이 아직 없어요"
- ☐ 측정값 표시(value) — 16 bold rounded (인라인 + ignore)
- ☐ AddProgressPhotoView — 사진 선택 후 미리보기 정상
- ☐ **ProgressPhotoView EmptyState** — camera 아이콘 + "진행 사진이 아직 없어요"

### 2-10. GoalSetting — GoalSettingView / GoalProgressView / AddGoalView / EditGoalView
- ☐ **NoGoalPlaceholder** — 다크 hero 안 "목표가 아직 없어요"
- ☐ Active goal 카드 정렬
- ☐ **EmptyProgressState** — chart.line.uptrend.xyaxis + "진행률 데이터가 아직 없어요"
- ☐ 진행률 링 26→28 rounded 숫자 (1~2px 커짐) — 카드 안에서 안 답답함?

### 2-11. Retrospective — WeeklyRetrospectiveView
- ☐ **EmptyState** — chart.bar.doc.horizontal + "이 주에는 기록이 아직 없어요"
- ☐ 주간 통계 카드들

### 2-12. MyPage — MyPageView
- (오늘 직접 손대지 않았지만 다크모드/Dynamic Type 영향 가능)
- ☐ 헤더 / 로그아웃 / 설정 진입

---

## 3. 회귀 발견 시 기록

발견한 회귀를 아래에 추가 → 본인이 해결하거나 다음 세션에서 처리.

```
- [화면] [요소] — [문제 설명]
  제안: [어떻게 고칠지 — 토큰 조정 vs 인라인 ignore vs 디자인 의도 재확인]
```

(여기에 점검 결과 추가)

---

## 4. 점검 후 후속 액션 후보

회귀 결과에 따라 다음 중 선택:

1. **토큰 값 미세 조정** — 예: `Spacing.lg`를 16 → 14로 되돌리면 폼 필드 영향. 전체 일관성 vs 개별 화면 선호 트레이드오프.
2. **개별 화면 ignore** — 회귀가 1~2곳뿐이면 그 줄만 인라인 + `// design-lint:ignore` 표시하고 토큰은 유지.
3. **새 토큰 추가** — 같은 컨텍스트가 반복되면 `numeralSmall(16/bold rounded)` 같은 토큰 신설.
4. **디자인 의도 재확인** — rounded vs system 디자인은 브랜드 표현 결정 사안. `brandWordmark`만 rounded 유지하기로 했으나, 헤더에도 rounded 살리고 싶으면 `displayRoundedMd` 등 토큰 추가.
