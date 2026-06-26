# 다중 활성 목표 처리 계획 (목표 위젯)

> 작성일: 2026-06-08
> 컨텍스트: 현재 `HomeViewModel`은 활성 목표 1개만 가정해서 `goals.content.first(where: status == .ACTIVE)`로 뽑는다. 위젯도 그 단일 목표만 보여줌. 향후 사용자가 동시에 여러 목표(예: 체중 감량 + 근육 증가)를 운영할 때의 처리 방향.

---

## 1. 현재 상태와 한계

- `HomeViewModel.activeGoal`: `GoalSummary?` — 단일 활성 목표
- 위젯 스냅샷 `GoalWidgetSnapshot.goal`: `ActiveGoal?` — 마찬가지로 단일
- 두 개 이상의 ACTIVE 목표가 존재해도 첫 번째만 보여주고 나머지는 위젯에서 안 보임 → 사용자가 다른 목표의 진행률을 못 봄
- 위젯 탭 시 항상 첫 번째 목표 상세로만 이동

## 2. 결정 포인트 — 어느 시점에 다중 처리하는가

### A. 표시 정책 (위젯 본체)

| 옵션 | 설명 | Pros | Cons |
|---|---|---|---|
| A1. 첫 번째만 표시 (현재) | 가장 우선순위 높은 1개만 | 단순 | 다른 목표 정보 누락 |
| A2. 위젯 인스턴스마다 사용자가 선택 | `IntentConfiguration` + AppIntent로 사용자가 위젯 추가 시 목표를 고름 | 사용자 의도 명확, 같은 위젯을 여러 개 추가해 모든 목표 동시 노출 가능 | AppIntent 도입 비용, Medium 차트 데이터 양이 늘어남 |
| A3. 라운드 로빈 (자동 순환) | 30분 또는 자정마다 다른 목표 보여줌 | 사용자가 설정 안 해도 모든 목표 노출 | 한눈에 진행률을 못 보고 우연히 보임, 디버깅 까다로움 |
| A4. Medium에 2개 동시 | 좌측 목표1 링 + 우측 목표2 링 (체중 차트 자리 교체) | 한 위젯에 핵심 정보 압축 | Medium은 좁아서 두 개는 시각적으로 빈약, 우측 차트의 가치 손실 |

**추천: A2 (IntentConfiguration)** — Apple의 표준 패턴이고, 잠금화면 Accessory도 같은 모델로 자연스럽게 확장 가능. A3는 명료성을 잃고 A4는 정보 밀도가 떨어짐.

### B. 라우팅 정책 (위젯 탭 → 어디로?)

| 옵션 | 설명 |
|---|---|
| B1. 현재 표시 중인 목표의 상세 | 위젯에 표시된 그 목표로 직진 (A2와 자연스럽게 짝) |
| B2. 활성 목표 목록 화면 | GoalSettingView로 가고 사용자가 다시 고름 — 다중 시점에 자연스러움 |
| B3. 단일 목표 상세를 비활성화하고 항상 목록 | 단순 일관성, UX 단조 |

**추천: B1** — 정보→액션 거리 가장 짧음. A2와 결합 시 "각 위젯이 자신의 목표로 진입"이 명확함.

---

## 3. 권장 구현 단계 (점진적)

### Phase 3.1 — 데이터 다중화 (1일)

- `HomeViewModel.activeGoals: [GoalSummary]` 도입 (기존 `activeGoal: GoalSummary?`는 첫 번째로 derived computed property로 유지하여 다른 화면 영향 최소화)
- `loadGoalProgress` 병렬 호출 (현재 첫 번째 1개만 호출 중)
- `GoalWidgetSnapshot.goals: [ActiveGoal]` (배열로 변경) + 단일 호환 helper
- 위젯은 여전히 첫 번째만 표시 (시각 동작 변화 없음)

### Phase 3.2 — IntentConfiguration 도입 (1.5일)

- 새 AppIntent `SelectGoalIntent`: `@Parameter(title: "목표") var goalEntity: GoalEntity`
- `GoalEntity: AppEntity` + `DynamicQueryStruct` — App Group 캐시(`activeGoals`)에서 목표 후보 enumeration
- `IntentTimelineProvider` 채택, `recommendations()`에서 활성 목표 각각의 추천 인스턴스 반환 (위젯 갤러리에서 "체중 감량", "근육 증가" 미리 추천)
- 위젯 탭 시 `widgetURL`은 `gainsy://widget/goal?id={goalId}` 형태로 변경, 라우터에서 query 파싱

### Phase 3.3 — 잠금화면 Accessory 다중 (선택, 0.5일)

- Accessory Circular/Rectangular도 같은 Intent로 목표 선택 가능
- 사용자가 잠금화면에 목표 2개를 각각 Circular로 추가하면 동시 노출

### Phase 3.4 — 기존 화면 UI 다중 대응 (별도 계획)

- HomeView `GoalProgressCard`가 캐러셀(PageView)이나 스택 형태로 다중 표시
- GoalSettingView는 이미 multi-aware인지 점검

---

## 4. 마이그레이션 시 호환성

- 기존 `GoalWidgetSnapshot.goal: ActiveGoal?` → 신규 `goals: [ActiveGoal]`로 바꿀 때 **버전 키 분리**:
  - 신규 키: `widget.goal.snapshot.v2`
  - 위젯 Provider는 v2 우선, 없으면 v1 fallback (1회 마이그레이션 후 v1 삭제)
- App Intent 도입 시 기존 StaticConfiguration 위젯도 한 패밀리 라인 더 유지하는 것을 고려 (사용자가 위젯 갤러리에서 둘 다 보이게 → 자연스러운 deprecation)

## 5. 측정 지표

도입 후 30일:
- Intent 위젯 추가율 / 전체 활성 사용자
- 위젯 탭 → 목표 상세 진입 → 기록 추가 전환율
- 단일 → 다중 목표로 늘어난 사용자 비율 (위젯이 유도하는가?)

## 6. 의사결정 점검

- 활성 목표 동시 운용은 백엔드에서 이미 허용되는가? `goal.status == .ACTIVE`를 여러 개 가질 수 있는지 확인 필요.
- 도메인 관점: 동시 활성을 권장할지(추천) / 가이드만 허용할지 / 막을지 — 위젯 UX는 도메인 결정 뒤에 정함.
- AppIntent 적용 최소 OS: iOS 17+ (현재 프로젝트의 deployment target 확인 필요).

---

## 7. 다음 액션

1. 백엔드/도메인: ACTIVE 동시 운용 허용 여부 결정
2. 결정이 "허용"이면 Phase 3.1부터 시작
3. 결정이 "단일 강제"면 이 문서는 보류, 위젯은 현 상태 유지
