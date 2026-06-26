# 테스트 커버리지 측정 절차

**기준일:** 2026-06-25
**목적:** 포트폴리오, README, 정량 지표 문서에 테스트 커버리지 수치를 쓰기 전에 재현 가능한 측정 경로를 고정한다.

---

## 원칙

- 커버리지 수치는 Jacoco 또는 Xcode `xccov` 리포트로 확인된 값만 확정 수치로 쓴다.
- 테스트 실패가 있으면 커버리지 수치를 포트폴리오용 성과 지표로 쓰지 않는다.
- 로컬 환경 의존 실패가 있으면 실패 테스트명, 원인, 필요한 외부 의존성을 함께 적는다.
- 커버리지 목표와 실제 커버리지 수치를 구분한다. 목표만 있는 상태에서는 `80% 달성`처럼 쓰지 않는다.

---

## 백엔드

### 실행

```sh
cd backend
./gradlew test jacocoTestReport --no-daemon
```

### 결과 위치

| 산출물 | 경로 |
|---|---|
| 테스트 리포트 | `backend/build/reports/tests/test/index.html` |
| Jacoco HTML | `backend/build/reports/jacoco/test/html/index.html` |
| Jacoco XML | `backend/build/reports/jacoco/test/jacocoTestReport.xml` |

### 라인 커버리지 추출

```sh
perl -ne 'if (/<counter type="LINE" missed="(\d+)" covered="(\d+)"/) { $m=$1; $c=$2 } END { printf "line %.2f%%\n", 100*$c/($m+$c) }' \
  backend/build/reports/jacoco/test/jacocoTestReport.xml
```

---

## iOS

### 실행

```sh
cd ios
scripts/coverage.sh
```

기본 destination은 현재 Mac에서 사용 가능한 첫 번째 iPhone 시뮬레이터를 자동 선택한다. 다른 시뮬레이터를 쓸 때는 환경변수로 바꾼다.

```sh
cd ios
DESTINATION='platform=iOS Simulator,name=iPhone 16 Pro,OS=latest' scripts/coverage.sh
```

### 결과 위치

| 산출물 | 경로 |
|---|---|
| Xcode result bundle | `ios/build/coverage/HealthCareTests.xcresult` |
| 커버리지 요약 | `xcrun xccov view --report ios/build/coverage/HealthCareTests.xcresult` |

---

## 정량 지표 문서 반영 기준

`docs/product-specs/GAINSY_QUANTIFIED_PROGRESS.md`에는 아래 형태로만 반영한다.

| 상태 | 표기 |
|---|---|
| 테스트 통과 + 리포트 생성 완료 | `라인 커버리지 n.n%, 브랜치 커버리지 n.n%` |
| 테스트 실패 또는 리포트 미생성 | `미측정` |
| 환경 의존 실패 | `조건부 미측정`, 실패 테스트와 외부 의존성 기록 |

커버리지 수치를 README, 포트폴리오, 이력서 문구에 옮길 때는 기준일과 측정 명령을 함께 확인한다.
