# VITALITY — 헬스케어 iOS 앱 디자인 검토 및 개선

## 앱 개요

VITALITY는 한국 사용자를 위한 퍼스널 헬스 트래킹 iOS 앱이다. SwiftUI 기반이며 운동, 식단, 신체 변화, 목표 설정을 추적한다.

## 현재 디자인 상태

### 색상 팔레트 (Dark Forest Green)
- `brandPrimary` = `#1A4A2E` — 진한 녹색 (헤더/버튼)
- `brandSecondary` = `#2D6A4F` — 중간 녹색
- `brandAccent` = `#52B788` — 밝은 민트 그린
- `brandSurface` = `#D8F3DC` — 연한 민트 배경
- `brandLight` = `#F0FAF3` — 거의 흰 민트
- 상태 컬러: Success `#40916C`, Warning `#F4A261`, Danger `#E63946`

### 타이포그래피
- displayLarge: 34pt bold rounded
- displayMedium: 28pt bold rounded
- headingLarge: 22pt bold
- headingMedium: 18pt semibold
- bodyLarge/Medium: 17pt/15pt regular
- caption: 12pt regular/semibold

### 주요 화면 구조
- **홈(대시보드)**: 다크 그린 웨이브 히어로 헤더 + 오늘의 요약 카드(링) + 기록 CTA + 3개 섹션(플랜/식단/운동)
- **다이어리**: 캘린더 기반 히스토리
- **탐색(Explore)**: 피드/인사이트
- **프로필(MyPage)**: 설정/정보

### 탭 내비게이션
4탭: 대시보드 | 다이어리 | 탐색 | 프로필

### 주요 디자인 요소
- WaveBackground: 진한 녹색 + 타원 오버레이 + 웨이브 커브
- SnapshotCard: 원형 진행 링 × 2 (칼로리/활동)
- PlanCardView: mint 배경 + 진행 바
- WorkoutRoutineCard: 다크 그린 배경 + 타원 오버레이
- MealLogCard: 130×110 카드 + 이모지 + 매크로 정보

## 현재 디자인의 문제점 (Generator가 분석)

분석 대상 파일:
- `ios/HealthCare/DesignSystem/Colors.swift`
- `ios/HealthCare/DesignSystem/Typography.swift`
- `ios/HealthCare/DesignSystem/Components/`
- `ios/HealthCare/Features/Home/Views/HomeView.swift`
- `ios/HealthCare/Features/Record/Hub/Views/RecordHubView.swift`
- `ios/HealthCare/Features/GoalSetting/Views/`
- `ios/HealthCare/Features/Auth/Views/`
- `ios/HealthCare/Navigation/MainTabView.swift`

## 목표

1. **시각적 탁월함**: 앱스토어 피처드 수준의 UI
2. **일관성**: 모든 화면에서 통일된 디자인 언어
3. **독창성**: 일반적인 헬스앱 템플릿을 벗어난 차별화
4. **감성**: 사용자가 매일 열고 싶은 앱
5. **정보 계층**: 핵심 데이터가 즉시 눈에 들어오는 레이아웃

## 디자인 방향성 제안

**Premium Dark × Nature** — 깊은 숲의 고요함과 생명력을 담은 프리미엄 헬스 앱
- 다크 그린 브랜드 아이덴티티 강화
- 데이터 시각화에 더 많은 공간과 강조
- 카드 기반 → 더 풍부한 레이아웃으로 진화
- 한국 사용자 취향: 깔끔함 + 정보 밀도

## 범위

Generator는 다음 중 **가장 임팩트가 큰 3-5개 파일**을 개선한다:
- 홈 화면이 최우선 (가장 많이 보는 화면)
- 디자인 시스템 (Colors, Typography, Components)
- Record Hub (두 번째로 중요한 진입점)
