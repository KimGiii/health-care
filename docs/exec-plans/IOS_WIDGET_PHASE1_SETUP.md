# Phase 1 — Widget Extension 타겟 추가 가이드

> Xcode GUI에서만 가능한 작업. 본 문서를 따라 수동으로 진행한다.
> 코드/엔타이틀먼트 변경(App Group, URL 스킴, 공유 레이어)은 이미 완료되어 있다.

---

## 사전 완료 사항 (자동화됨)

- ✅ `HealthCare.entitlements` — `group.com.kingloo.gainsy.widgets` App Group 추가됨
- ✅ `Info.plist` — `gainsy://` URL 스킴 등록됨
- ✅ `Core/Widget/` 공유 코드 작성됨
  - `WidgetConstants.swift`
  - `WidgetSnapshot.swift`
  - `WidgetDataStore.swift`
  - `WidgetReloadCenter.swift`

---

## 1. Apple Developer Portal — App Group 등록

1. https://developer.apple.com/account → Identifiers → "+" → **App Groups**
2. Identifier: `group.com.kingloo.gainsy.widgets`
3. Description: `Gainsy App Group`
4. Continue → Register

이후 메인 앱 App ID(`com.kingloo.gainsy.ios`)의 Capabilities에서 App Groups를 활성화하고 위 그룹을 선택. (위젯 App ID는 2번 단계에서 생성 후 동일하게 적용)

---

## 2. Widget Extension 타겟 생성

1. Xcode에서 `HealthCare.xcodeproj` 열기
2. File → New → Target...
3. **Widget Extension** 선택 → Next
4. 옵션:
   - Product Name: `HealthCareWidget`
   - Team: 기존 메인 앱과 동일
   - Bundle Identifier: `com.kingloo.gainsy.ios.HealthCareWidget` (자동 채워짐)
   - Language: Swift
   - **"Include Live Activity" 체크 해제** (Phase 1 단계에서는 불필요)
   - **"Include Configuration App Intent" 체크 해제** (Phase 2에서 결정)
5. Finish → "Activate" 다이얼로그가 뜨면 **Activate** 클릭 (스킴 자동 생성)

---

## 3. Widget 타겟 Capabilities 설정

1. Project Navigator → `HealthCare` 프로젝트 선택
2. TARGETS → `HealthCareWidgetExtension` 선택
3. **Signing & Capabilities** 탭
4. "+ Capability" → **App Groups** 추가
5. `group.com.kingloo.gainsy.widgets` 체크

> 만약 그룹이 목록에 없다면 Apple Developer Portal의 1번 단계가 완료되지 않은 것. 새로고침 버튼 클릭.

---

## 4. 공유 코드를 Widget 타겟에도 포함

다음 4개 파일을 Widget 타겟의 **Target Membership**에 추가:

- `HealthCare/Core/Widget/WidgetConstants.swift`
- `HealthCare/Core/Widget/WidgetSnapshot.swift`
- `HealthCare/Core/Widget/WidgetDataStore.swift`
- `HealthCare/Core/Widget/WidgetReloadCenter.swift`

방법:
1. 각 파일 선택
2. 우측 File Inspector → **Target Membership** 섹션
3. `HealthCare` (기존 체크 유지) + `HealthCareWidgetExtension` 체크

> ⚠️ `WidgetReloadCenter`는 `WidgetKit`을 import하지만 `#if canImport(WidgetKit)`으로 가드되어 있어 양쪽 타겟 모두 안전.

---

## 5. Phase 1 빌드 검증

생성된 기본 위젯(`HealthCareWidget.swift`)이 빌드되는지 확인:

1. 스킴을 `HealthCareWidgetExtension` 으로 변경
2. ⌘B 빌드 → 에러 없어야 함
3. ⌘R 실행 → 시뮬레이터 홈 화면 길게 눌러 위젯 추가 시 "HealthCareWidget" 표시 확인

### 빠른 데이터 공유 테스트 (선택)

메인 앱의 임시 위치(예: `HomeView.onAppear`)에 한 줄 넣고 실행:

```swift
WidgetDataStore()?.saveCalorie(.placeholder)
```

이후 위젯 익스텐션의 Provider에서 다음으로 읽어 콘솔 출력되면 App Group 통신 성공:

```swift
let snap = WidgetDataStore()?.loadCalorie()
print("[Widget] loaded:", snap as Any)
```

→ Phase 2 시작 전 위 테스트 코드는 제거한다.

---

## 다음 단계 (Phase 2)

- `CalorieEntry` / `CalorieProvider` / `CalorieWidgetView` 작성
- `HomeViewModel`에서 데이터 로드 후 `WidgetDataStore.saveCalorie(...)` 호출 삽입
- 식단 기록 완료 후 `WidgetReloadCenter.reloadCalorie()` 호출
- Small / Medium 두 패밀리 디자인 구현 (Color.brand 진행 링)
