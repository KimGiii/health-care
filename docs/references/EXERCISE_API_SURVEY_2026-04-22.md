# 운동 종목·칼로리 API 조사

작성일: 2026-04-22  
조사 기준: 공식 문서 / 공식 사이트 우선  
대상 독자: 기획, 백엔드, iOS, 아키텍처

---

## 1. 조사 목적

우리 서비스에 운동 종목 데이터와 칼로리 소모 추정 데이터를 어떤 방식으로 연동하는 것이 적절한지 판단하기 위해 외부 API 및 기준 데이터 소스를 조사한다.

이번 조사는 단순히 "붙일 수 있는 API가 있는가"를 넘어서 아래 질문에 답하는 것을 목표로 한다.

- 운동 종목 목록을 보강하는 데 적합한가
- 칼로리 소모 계산을 서비스 핵심 로직으로 맡길 만한가
- 한국어 중심 UX에 맞는가
- 현재 우리 서비스 구조와 충돌하지 않는가
- MVP 시점에 도입 가치가 복잡도보다 큰가

---

## 2. 평가 기준

후보는 아래 기준으로 비교했다.

- 제공 기능: 운동 목록, 자연어 입력, 칼로리 계산, MET 제공 여부
- 연동 방식: 인증 방식, 요청/응답 구조, 서버 연동 난이도
- 제품 적합성: 한국어 UX 적합성, 내부 데이터 모델과의 정합성
- 운영 리스크: 비용, 벤더 종속성, 응답 일관성, 장기 유지보수성
- 현재 구조 적합성: 내부 `exercise_catalog`, `met_value`, 세션 저장 흐름과의 궁합

---

## 3. 현재 우리 서비스 구조 요약

조사 결과는 현재 저장소 구조를 기준으로 해석해야 한다.

- 백엔드는 `exercise_catalog.met_value`를 저장하고, 세션 저장 시 내부 로직으로 칼로리를 계산한다.
- 운동 기록 UX는 현재 내부 카탈로그 검색 후 세트를 구성하는 흐름이다.
- 식단 도메인에는 이미 `외부 검색 -> 정규화 DTO -> 선택적 import` 패턴이 존재한다.
- 따라서 운동 도메인도 외부 API를 붙인다면 `실시간 세션 저장 경로`보다는 `검색/보강/import 보조 경로`가 자연스럽다.

참고 코드:

- `backend/src/main/java/com/healthcare/domain/exercise/service/ExerciseSessionService.java`
- `backend/src/main/java/com/healthcare/domain/exercise/entity/ExerciseCatalog.java`
- `backend/src/main/java/com/healthcare/domain/diet/external/service/ExternalFoodSearchService.java`
- `ios/HealthCare/Features/Record/Exercise/ViewModels/AddExerciseSessionViewModel.swift`

---

## 4. 후보별 조사

### 4.1 Nutritionix

공식 소스:

- https://developer.nutritionix.com/docs/v2
- https://docx.syndigo.com/developers/docs/natural-language-for-exercise

요약:

- 자연어 기반 운동 입력 처리에 강점이 있다.
- `"30 min running"`, `"45 minutes yoga"` 같은 표현을 입력하면 운동명, 시간, 추정 칼로리 등을 구조화해 반환하는 형태다.
- 공식 문서 기준으로 앱 ID / 앱 키 기반 인증 헤더를 사용한다.

제공 기능:

- 자연어 운동 입력 파싱
- 칼로리 추정
- MET 관련 필드 제공
- Compendium 코드 매핑 정보 제공

장점:

- 빠른 PoC에 유리하다.
- 사용자가 자유 서술로 운동을 입력하는 UX를 실험하기 좋다.
- structured response로 정규화하기 쉽다.

제약:

- 영어 중심 입력 경험을 전제로 설계되어 있다.
- 한국어 운동명, 한국식 표현, 혼합 문장 대응은 별도 번역/매핑 레이어가 필요하다.
- 칼로리 계산 로직을 벤더에 위임하게 되면 내부 기준식과 일관성을 유지하기 어렵다.
- 세션 저장 시점마다 외부 API를 호출하면 write path가 외부 장애에 영향을 받는다.

우리 서비스 적합성 판단:

- `빠른 실험용 PoC`: 적합
- `운영 핵심 칼로리 엔진`: 비추천
- `MVP 기본 검색/저장 경로`: 비추천

도입 분류: `보조 도입 가능`

---

### 4.2 API Ninjas

공식 소스:

- https://api-ninjas.com/api/caloriesburned
- https://api-ninjas.com/api/exercises

요약:

- `Calories Burned API`와 `Exercises API`가 분리되어 있다.
- 단순한 HTTP API 형태라서 붙이기는 쉽다.
- 활동명, 체중, 시간 등을 기반으로 칼로리 추정을 수행하는 용도로 적합하다.

제공 기능:

- 칼로리 소모 추정
- 운동/활동 목록 조회
- 상대적으로 단순한 query parameter 기반 호출

장점:

- PoC 속도가 빠르다.
- 서버 연동 난이도가 낮다.
- 운동 카탈로그 탐색과 칼로리 계산을 분리해서 실험하기 쉽다.

제약:

- 한국어 친화적 UX를 직접 제공하지 않는다.
- 내부 운동 카탈로그 체계와 1:1로 맞추려면 별도 정규화가 필요하다.
- 칼로리 계산 결과를 그대로 저장 기준으로 쓰면 내부 `met_value` 기반 체계와 충돌할 수 있다.
- 정확도와 기준식 통제권이 내부에 남지 않는다.

우리 서비스 적합성 판단:

- `백오피스 비교 실험`: 가능
- `세션 저장 시 실시간 계산 엔진`: 비추천
- `MVP 기본 카탈로그`: 비추천

도입 분류: `보조 도입 가능`

---

### 4.3 wger

공식 소스:

- https://wger.readthedocs.io/en/latest/api/api.html
- https://wger.de/en/software/api

요약:

- 운동, 루틴, 장비, 근육 등 운동 데이터베이스 성격이 강하다.
- 칼로리 계산 엔진보다는 운동 카탈로그 보강 쪽이 핵심 활용 지점이다.
- 공개 API 문서와 오픈소스 기반이라는 점이 장점이다.

제공 기능:

- 운동 목록 / 운동 메타데이터
- 운동 관련 구조화 데이터 접근
- 운동 라이브러리 확장에 유용한 카탈로그형 API

장점:

- 운동 마스터 데이터 보강 관점에서 해석하기 좋다.
- 벤더 종속성이 상대적으로 덜한 편이다.
- 관리자 배치 또는 시드 보강 용도로 활용 방향이 명확하다.

제약:

- 칼로리 계산을 직접 책임지는 서비스로 보기 어렵다.
- 한국어 이름과 설명을 그대로 신뢰하기보다는 내부 정규화가 필요하다.
- 사용자 기록 write path에 실시간으로 넣을 이유가 약하다.

우리 서비스 적합성 판단:

- `카탈로그 enrichment`: 적합
- `MVP 세션 저장 경로`: 비추천
- `칼로리 계산 엔진`: 비추천

도입 분류: `보조 도입 가능`

---

### 4.4 Compendium of Physical Activities

공식 소스:

- https://pacompendium.com/
- https://pacompendium.com/adult-compendium

요약:

- API 서비스라기보다 활동별 MET 기준 데이터 레퍼런스다.
- 실무에서는 외부 벤더 API보다 내부 칼로리 계산 기준을 세우는 참조 소스로 더 가치가 있다.
- 우리 서비스가 이미 `exercise_catalog.met_value` 필드를 보유하고 있다는 점에서 가장 자연스럽게 연결된다.

제공 기능:

- 활동별 MET 기준값
- 분류 체계와 코드 체계
- 내부 카탈로그 정제 기준 제공

장점:

- 내부 계산 기준을 통제할 수 있다.
- 벤더 장애가 운영 write path에 영향을 주지 않는다.
- 칼로리를 계속 `추정치`로 안내하는 현재 제품 방향과 잘 맞는다.
- 운동 카탈로그와 한국어 명칭 체계를 내부에서 유지할 수 있다.

제약:

- 바로 호출 가능한 검색 API가 아니다.
- 별도 정제, 시드 보강, 내부 매핑 작업이 필요하다.
- 사용자 자유 입력을 직접 처리해주지는 않는다.

우리 서비스 적합성 판단:

- `MVP 기준 MET 레퍼런스`: 매우 적합
- `칼로리 계산의 기준 데이터`: 적합
- `실시간 검색 API 대체재`: 부적합

도입 분류: `즉시 도입 가능`

---

## 5. 비교표

| 후보 | 운동 목록 | 칼로리 계산 | 자연어 입력 | 인증/도입 난이도 | 한국어 적합성 | 현재 구조 적합성 | 권장 분류 |
|---|---|---|---|---|---|---|---|
| Nutritionix | 부분 가능 | 가능 | 강함 | 중간 | 낮음 | PoC는 가능하나 운영 핵심 경로와는 충돌 가능 | 보조 도입 가능 |
| API Ninjas | 가능 | 가능 | 약함 | 낮음 | 낮음 | 비교 실험용으로는 가능하나 핵심 경로에는 부적합 | 보조 도입 가능 |
| wger | 강함 | 약함 | 없음에 가까움 | 중간 | 낮음~중간 | 카탈로그 보강용으로 적합 | 보조 도입 가능 |
| Compendium | 직접 목록 API 아님 | 내부 계산 기준으로 적합 | 없음 | 중간 | 내부 정규화 전제 시 높음 | 현재 `met_value` 구조와 가장 잘 맞음 | 즉시 도입 가능 |

---

## 6. 우리 서비스와의 적합성 해석

### 6.1 왜 외부 API를 write path에 직접 넣지 않는가

우리 서비스의 운동 기록 핵심 경로는 아래와 같다.

1. 사용자가 내부 카탈로그에서 운동을 선택한다.
2. 세트 정보를 입력한다.
3. 서버가 세션을 저장하고 내부 기준으로 칼로리를 계산한다.

이 흐름에 실시간 외부 API 호출을 넣으면 다음 문제가 생긴다.

- 외부 장애가 운동 저장 성공 여부에 직접 영향을 준다.
- 동일 운동이라도 벤더별 계산값 차이로 일관성이 깨질 수 있다.
- 한국어 운동명과 내부 카탈로그 정규화 체계가 약해진다.
- 나중에 공급자를 바꾸면 과거 기록과 현재 기록의 비교 기준이 흔들릴 수 있다.

### 6.2 왜 Compendium 기반 내부 계산이 더 적합한가

- 이미 `exercise_catalog.met_value` 필드가 존재한다.
- 이미 세션 저장 시 내부 칼로리 계산이 구현되어 있다.
- 사용자에게 "정확값"이 아니라 "추정치"를 제공하는 제품 방향과 맞다.
- 내부 카탈로그를 유지하면 `name_ko`, 운동 분류, muscle group을 우리 서비스 기준으로 통제할 수 있다.

### 6.3 외부 API가 필요한 지점은 어디인가

외부 API는 아래와 같은 보조 용도로는 의미가 있다.

- 초기 카탈로그 수집 및 비교
- 관리자 배치 기반 enrichment
- 추후 사용자 검색 품질 개선 실험
- 내부 카탈로그에 없는 운동 후보 탐색

반대로 아래 용도에는 우선순위가 낮다.

- 세션 저장 직전 실시간 칼로리 계산
- 앱 기본 검색 UX의 1차 데이터 소스
- 사용자 기록의 최종 진실값 계산

---

## 7. 권장 분류

### 즉시 도입 가능

- `Compendium of Physical Activities`

권장 이유:

- 현재 데이터 모델과 가장 잘 맞는다.
- 운영 안정성을 해치지 않는다.
- 내부 카탈로그 품질 개선이라는 다음 액션이 명확하다.

### 보조 도입 가능

- `wger`
- `Nutritionix`
- `API Ninjas`

권장 이유:

- 운영 핵심 경로가 아니라 보조 도입일 때만 가치가 크다.
- 각각 `카탈로그 보강`, `자연어 실험`, `빠른 PoC`라는 명확한 쓰임새가 있다.

### 기본 비추천

- 외부 API를 운동 세션 저장 시점의 필수 호출 경로로 넣는 방식
- 외부 API 결과를 그대로 최종 칼로리 truth로 저장하는 방식
- 한국어 운동명 체계를 외부 공급자 명칭에 직접 의존하는 방식

비추천 이유:

- 서비스 핵심 기록 루프의 안정성 저하
- 데이터 기준 불일치
- 벤더 종속성 증가
- 한국어 UX 품질 저하 가능성

---

## 8. 권장 결론

MVP 기준 권장안은 아래와 같다.

- 운동 카탈로그와 칼로리 계산의 system of record는 내부 백엔드가 유지한다.
- `Compendium`를 기준 MET 레퍼런스로 삼아 기존 `exercise_catalog.met_value`를 정제한다.
- 현재 `GET /api/v1/exercise/catalog`와 `POST /api/v1/exercise/sessions` 중심 흐름은 유지한다.
- `wger`, `Nutritionix`, `API Ninjas`는 운영 핵심 경로가 아니라 비교 실험 또는 관리자 보강 용도로만 검토한다.

이 판단은 현재 우리 서비스가 추구하는 가치와도 맞다.

- 빠른 입력
- 한국어 중심 UX
- 내부 데이터 통제
- 운영 안정성
- 일관된 추정 기준

---

## 9. 다음 액션 제안

지금 바로 할 일:

- 기존 `exercise_catalog` 시드의 `met_value`를 Compendium 기준으로 검토
- 한국어 운동명 정규화 원칙 수립
- 카디오 / 근력 / 맨몸 운동별 MET 보정 기준 정의

후속 검토:

- 관리자용 외부 운동 import 시나리오가 실제로 필요한지 확인
- 사용자 검색 실패 로그가 쌓이면 `external-search + import` 패턴을 별도 검토
- 자연어 운동 입력 실험이 필요해지면 `Nutritionix` PoC를 별도 트랙으로 진행

---

## 10. 참고 링크

- Nutritionix API Home: https://developer.nutritionix.com/docs/v2
- Nutritionix Natural Language for Exercise: https://docx.syndigo.com/developers/docs/natural-language-for-exercise
- API Ninjas Calories Burned API: https://api-ninjas.com/api/caloriesburned
- API Ninjas Exercises API: https://api-ninjas.com/api/exercises
- wger API 문서: https://wger.readthedocs.io/en/latest/api/api.html
- wger API 소개: https://wger.de/en/software/api
- Compendium of Physical Activities: https://pacompendium.com/
- Adult Compendium: https://pacompendium.com/adult-compendium
