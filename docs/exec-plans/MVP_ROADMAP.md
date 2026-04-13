# Health Care 서비스 MVP 개발 로드맵

**작성일:** 2026-04-10  
**버전:** 1.1  
**대상:** 개발팀  
**상태:** 실행 대기  
**개발 전략:** iOS 네이티브 우선 → Android 추가

---

## 목차

1. [로드맵 개요](#1-로드맵-개요)
2. [Phase별 실행 계획](#2-phase별-실행-계획)
3. [도메인별 구현 우선순위](#3-도메인별-구현-우선순위)
4. [기술 스택별 작업 흐름](#4-기술-스택별-작업-흐름)
5. [검증 및 테스트 전략](#5-검증-및-테스트-전략)
6. [배포 및 출시 준비](#6-배포-및-출시-준비)

---

## 1. 로드맵 개요

### 1.1 전체 개발 흐름

```
Phase 0: 환경 구축
    ↓
Phase 1: 인증 & 사용자
    ↓
Phase 2: 운동 기록 (가장 단순)
    ↓
Phase 3: 식단 기록 (API 연동)
    ↓
Phase 4: 신체 측정 & 진행 사진
    ↓
Phase 5: 목표 & 인사이트
    ↓
Phase 6: MVP 출시 준비
```

### 1.2 개발 원칙

**순차적 검증:**
- 각 Phase는 이전 Phase 완료 후 시작
- 백엔드 API → 모바일 연동 순서로 진행
- 기능별 E2E 테스트 완료 후 다음 Phase 진입

**수직적 슬라이싱:**
- 각 Phase는 완결된 사용자 가치 제공
- "사용자가 X를 할 수 있다" 형태의 명확한 완료 조건
- 부분 배포 및 테스트 가능한 단위

**기술 부채 최소화:**
- 아키텍처 설계 준수 (ARCHITECTURE.md)
- API 설계 준수 (API_DESIGN.md)
- DB 스키마 준수 (DB_SCHEMA.md)
- 테스트 커버리지 유지 (핵심 로직 80% 이상)

### 1.3 플랫폼별 작업 분할

| 플랫폼 | 담당 범위 | 기술 스택 |
|-------|---------|---------|
| **백엔드** | Spring Boot API, PostgreSQL, Redis, S3, FCM 통합 | Java 21, Spring Boot 3.x, Docker |
| **iOS 앱** | iOS 네이티브 앱 (MVP) | Swift, Xcode, UIKit/SwiftUI |
| **Android 앱** | Android 앱 (MVP 이후) | Kotlin, Android Studio (Phase 7) |
| **인프라** | Docker Compose (로컬), AWS (프로덕션) | Docker, AWS RDS/ElastiCache/S3/EC2 |

**개발 전략:**
- **Phase 0-6:** iOS 네이티브 앱 (Swift + Xcode) 개발
- **Phase 7 이후:** Android 앱 (Kotlin) 추가
- **백엔드:** iOS 개발과 병렬 진행 가능

---

## 2. Phase별 실행 계획

### Phase 0: 환경 구축 및 기반 인프라

**목표:** 로컬 개발 환경 및 CI/CD 파이프라인 구축

#### 백엔드 작업

1. **프로젝트 초기화**
   - [ ] Gradle 프로젝트 생성 (Java 21, Spring Boot 3.x)
   - [ ] 패키지 구조 생성 (`com.healthcare.*` - ARCHITECTURE.md 참조)
   - [ ] `application.yml` 설정 (local/dev/prod profiles)
   - [ ] `.gitignore` 설정

2. **로컬 인프라 구성**
   - [ ] `docker-compose.yml` 작성
     - PostgreSQL 16
     - Redis 7
     - LocalStack (S3 에뮬레이션)
   - [ ] DB 초기화 스크립트 작성 (`schema.sql`)
   - [ ] 로컬 환경에서 Spring Boot 앱 실행 확인

3. **공통 설정 구현**
   - [ ] `SecurityConfig.java` (기본 구조만, JWT는 Phase 1)
   - [ ] `RedisConfig.java`
   - [ ] `S3Config.java` (LocalStack 연동)
   - [ ] `WebMvcConfig.java` (CORS 설정)
   - [ ] `GlobalExceptionHandler.java` (에러 응답 구조)

4. **유틸리티 클래스 구현**
   - [ ] `DateUtil.java` (timezone-aware 날짜 처리)
   - [ ] `CalorieCalculator.java` (MET, Keytel 공식)
   - [ ] `BodyMetricsCalculator.java` (BMI, WHR, US Navy 공식)
   - [ ] `ExifStripper.java` (사진 메타데이터 제거)

5. **테스트 기반 구축**
   - [ ] JUnit 5 + Mockito 설정
   - [ ] TestContainers 설정 (PostgreSQL, Redis)
   - [ ] 통합 테스트 base class 작성

#### iOS 앱 작업

1. **Xcode 프로젝트 초기화**
   - [ ] Xcode에서 새 iOS App 프로젝트 생성
   - [ ] Bundle Identifier 설정 (com.healthcare.app)
   - [ ] Deployment Target: iOS 16.0 이상
   - [ ] Interface: SwiftUI 또는 UIKit 선택
   - [ ] 프로젝트 구조 설계
     ```
     HealthCare/
     ├── App/              (AppDelegate, SceneDelegate)
     ├── Models/           (데이터 모델)
     ├── Views/            (화면 UI)
     ├── ViewModels/       (비즈니스 로직)
     ├── Services/         (API 클라이언트, 로컬 저장소)
     ├── Utilities/        (헬퍼, 확장)
     └── Resources/        (Assets, Colors, Fonts)
     ```

2. **의존성 설정 (Swift Package Manager 또는 CocoaPods)**
   - [ ] Alamofire (HTTP 클라이언트)
   - [ ] KeychainAccess (토큰 저장)
   - [ ] Kingfisher (이미지 로딩/캐싱)
   - [ ] SwiftLint (코드 스타일)

3. **공통 컴포넌트 구현**
   - [ ] `APIClient.swift` (네트워크 계층)
   - [ ] `TokenManager.swift` (JWT 토큰 관리, Keychain 저장)
   - [ ] `ErrorHandler.swift` (에러 처리)
   - [ ] `NetworkMonitor.swift` (네트워크 상태 확인)

4. **개발 환경 설정**
   - [ ] `.xcconfig` 파일 (Debug/Release 환경 변수)
   - [ ] API Base URL 설정 (Debug: localhost, Release: 프로덕션)
   - [ ] `.gitignore` 설정 (Xcode, SPM)

#### 검증 기준

- ✅ 로컬에서 Spring Boot 실행 성공 (포트 8080)
- ✅ Docker Compose로 PostgreSQL, Redis, LocalStack 실행
- ✅ iOS 시뮬레이터에서 앱 실행 성공 (빈 화면)
- ✅ iOS 앱에서 백엔드 Health Check API 호출 성공 (`http://localhost:8080/actuator/health`)
- ✅ Xcode 빌드 에러 없음

---

### Phase 1: 인증 및 사용자 관리

**목표:** 사용자가 회원가입하고 로그인할 수 있다

**PRD 연결:** 섹션 7.5 (보안 및 개인정보), 섹션 12.5 (지원 환경)

#### 백엔드 작업

1. **DB 스키마 구현**
   - [ ] `users` 테이블 생성
   - [ ] `refresh_tokens` 테이블 생성
   - [ ] 마이그레이션 스크립트 작성 (Flyway 또는 Liquibase)

2. **인증 도메인 구현**
   - [ ] `User` 엔티티 (`domain/user/entity/User.java`)
   - [ ] `RefreshToken` 엔티티 (`domain/auth/entity/RefreshToken.java`)
   - [ ] `UserRepository`, `RefreshTokenRepository`
   - [ ] `AuthService` (회원가입, 로그인, 토큰 갱신, 로그아웃)
   - [ ] `UserService` (프로필 조회, 수정, 삭제)

3. **JWT 보안 구현**
   - [ ] `JwtTokenProvider.java` (토큰 생성, 검증, 클레임 추출)
   - [ ] `JwtAuthenticationFilter.java` (OncePerRequestFilter)
   - [ ] `CustomUserDetailsService.java`
   - [ ] `SecurityConfig.java` 완성 (JWT 필터 체인)

4. **API 엔드포인트 구현**
   - [ ] `POST /api/v1/auth/register` (회원가입)
   - [ ] `POST /api/v1/auth/login` (로그인)
   - [ ] `POST /api/v1/auth/token/refresh` (토큰 갱신)
   - [ ] `POST /api/v1/auth/logout` (로그아웃)
   - [ ] `GET /api/v1/users/me` (내 프로필 조회)
   - [ ] `PATCH /api/v1/users/me` (프로필 수정)
   - [ ] `DELETE /api/v1/users/me` (계정 삭제 - soft delete)

5. **테스트**
   - [ ] `AuthServiceTest` (단위 테스트)
   - [ ] `AuthControllerTest` (통합 테스트)
   - [ ] JWT 필터 테스트

#### iOS 앱 작업

1. **데이터 모델 구현**
   - [ ] `User.swift` (사용자 모델)
   - [ ] `AuthRequest.swift` (회원가입/로그인 요청)
   - [ ] `AuthResponse.swift` (토큰 응답)
   - [ ] `UserProfile.swift` (프로필 정보)

2. **네트워크 계층 구현**
   - [ ] `AuthAPI.swift` (인증 API 엔드포인트)
     - `register(email:password:displayName:...)`
     - `login(email:password:)`
     - `refreshToken(_:)`
     - `logout()`
   - [ ] `UserAPI.swift` (사용자 API)
     - `getProfile()`
     - `updateProfile(_:)`
     - `deleteAccount()`

3. **화면 구현 (SwiftUI 또는 UIKit)**
   - [ ] `SignUpView.swift` (회원가입)
     - 이메일, 비밀번호, 표시 이름 입력
     - 기본 신체 정보 (성별, 생년월일, 키, 체중)
     - 활동 수준 선택 (Picker)
     - 유효성 검사
   - [ ] `LoginView.swift` (로그인)
   - [ ] `ProfileView.swift` (프로필 조회)
   - [ ] `ProfileEditView.swift` (프로필 수정)

4. **상태 관리**
   - [ ] `AuthViewModel.swift`
     - 로그인 상태 관리 (@Published)
     - 회원가입/로그인/로그아웃 메서드
     - 토큰 자동 갱신 로직
   - [ ] `UserViewModel.swift`
     - 프로필 조회/수정

5. **토큰 관리**
   - [ ] Keychain에 Access Token 저장
   - [ ] Keychain에 Refresh Token 저장
   - [ ] API 호출 시 자동으로 토큰 포함 (Authorization Header)
   - [ ] 401 응답 시 자동 토큰 갱신 (Alamofire Interceptor)

#### 검증 기준

- ✅ 사용자가 회원가입할 수 있다
- ✅ 사용자가 로그인하여 JWT 토큰을 받는다
- ✅ 토큰으로 보호된 API 호출 성공
- ✅ 토큰 갱신 자동화 동작 확인
- ✅ 로그아웃 후 토큰 무효화 확인
- ✅ 프로필 수정 및 삭제 동작 확인

---

### Phase 2: 운동 기록

**목표:** 사용자가 운동 세션을 기록하고 조회할 수 있다

**PRD 연결:** 섹션 8.1 (운동 기록), 섹션 9.1 (운동 대시보드)

**우선순위 근거:** 운동 기록이 식단보다 단순 (외부 API 불필요)

#### 백엔드 작업

1. **DB 스키마 구현**
   - [ ] `exercise_catalog` 테이블 (운동 카탈로그)
   - [ ] `exercise_sessions` 테이블
   - [ ] `exercise_sets` 테이블
   - [ ] 초기 운동 데이터 시딩 (30개 이상 MET 값 포함)

2. **운동 도메인 구현**
   - [ ] `ExerciseCatalog` 엔티티
   - [ ] `ExerciseSession` 엔티티
   - [ ] `ExerciseSet` 엔티티
   - [ ] `ExerciseCatalogRepository`
   - [ ] `ExerciseSessionRepository`
   - [ ] `ExerciseSetRepository`

3. **비즈니스 로직 구현**
   - [ ] `ExerciseSessionService`
     - 세션 생성 (운동 세트 bulk insert)
     - 칼로리 소모량 계산 (MET 공식)
     - PR(Personal Record) 감지 로직
     - 세션 조회, 수정, 삭제
   - [ ] `ExerciseSummaryService`
     - 일간 집계 (총 운동 시간, 칼로리, 볼륨)
     - 주간 집계
     - Redis 캐싱 적용
   - [ ] `ExerciseCatalogService`
     - 운동 카탈로그 검색
     - 커스텀 운동 생성

4. **API 엔드포인트 구현**
   - [ ] `GET /api/v1/exercise/catalog` (운동 카탈로그 조회)
   - [ ] `POST /api/v1/exercise/catalog` (커스텀 운동 생성)
   - [ ] `POST /api/v1/exercise/sessions` (운동 세션 기록)
   - [ ] `GET /api/v1/exercise/sessions` (세션 목록 조회)
   - [ ] `GET /api/v1/exercise/sessions/{id}` (세션 상세 조회)
   - [ ] `PATCH /api/v1/exercise/sessions/{id}` (세션 수정)
   - [ ] `DELETE /api/v1/exercise/sessions/{id}` (세션 삭제)
   - [ ] `GET /api/v1/exercise/summary/daily` (일간 요약)
   - [ ] `GET /api/v1/exercise/summary/weekly` (주간 요약)

5. **테스트**
   - [ ] MET 칼로리 계산 단위 테스트
   - [ ] PR 감지 로직 테스트
   - [ ] 세션 CRUD 통합 테스트
   - [ ] 일간/주간 집계 테스트

#### iOS 앱 작업

1. **데이터 모델**
   - [ ] `Exercise.swift` (운동 카탈로그 모델)
   - [ ] `ExerciseSession.swift` (운동 세션)
   - [ ] `ExerciseSet.swift` (세트 정보)
   - [ ] `DailySummary.swift`, `WeeklySummary.swift`

2. **네트워크 계층**
   - [ ] `ExerciseAPI.swift`
     - `getCatalog(query:muscleGroup:)`
     - `createSession(_:)`
     - `getSessions(date:)`
     - `updateSession(id:_:)`
     - `deleteSession(id:)`
     - `getDailySummary(date:)`
     - `getWeeklySummary(startDate:)`

3. **화면 구현 (SwiftUI/UIKit)**
   - [ ] `ExerciseHomeView.swift` (운동 기록 홈)
   - [ ] `ExerciseCatalogView.swift` (운동 선택, 검색/필터)
   - [ ] `SetInputView.swift` (세트 입력)
     - 무게, 횟수, 휴식 시간 입력
     - 세트 추가/삭제
     - 실시간 볼륨/칼로리 계산 표시
   - [ ] `SessionSummaryView.swift` (세션 요약)
   - [ ] `ExerciseHistoryView.swift` (운동 히스토리)
   - [ ] `ExerciseDashboardView.swift` (일간/주간 대시보드)

4. **주요 기능 구현**
   - [ ] 운동 카탈로그 검색 (Searchable)
   - [ ] 근육군별 필터링 (Picker/Segmented Control)
   - [ ] 휴식 타이머 (Timer, Background mode)
     - 타이머 카운트다운
     - 완료 시 알림 (로컬 푸시)
   - [ ] PR 감지 및 배지 표시
   - [ ] 실시간 통계 계산 (총 볼륨, 칼로리)

5. **ViewModel**
   - [ ] `ExerciseViewModel.swift`
     - 세션 상태 관리
     - 카탈로그 검색 로직
     - 세트 추가/삭제 로직
   - [ ] `TimerViewModel.swift` (휴식 타이머)

#### 검증 기준

- ✅ 사용자가 운동을 선택하고 세트를 기록할 수 있다
- ✅ 총 볼륨과 칼로리가 자동 계산된다
- ✅ PR 달성 시 알림이 표시된다
- ✅ 과거 운동 기록을 조회할 수 있다
- ✅ 일간/주간 운동 요약을 확인할 수 있다

---

### Phase 3: 식단 기록

**목표:** 사용자가 식사를 기록하고 영양소를 추적할 수 있다

**PRD 연결:** 섹션 8.2 (식단 기록), 섹션 9.2 (식단 대시보드)

**핵심 도전:** USDA + Open Food Facts API 연동, 한국 음식 데이터 확보

#### 백엔드 작업

1. **DB 스키마 구현**
   - [ ] `food_catalog` 테이블 (음식 카탈로그)
   - [ ] `meals` 테이블
   - [ ] `meal_items` 테이블
   - [ ] 초기 한국 음식 데이터 시딩 (100개 이상)

2. **외부 API 클라이언트 구현**
   - [ ] `UsdaFoodDataClient.java`
     - USDA API 호출
     - 검색, 상세 조회
     - 에러 핸들링
   - [ ] `OpenFoodFactsClient.java`
     - Open Food Facts API 호출
     - 바코드 검색, 제품 검색
     - 에러 핸들링
   - [ ] `NutritionApiOrchestrator.java`
     - 계층적 검색 (캐시 → USDA → OFF)
     - 결과 병합 및 정규화

3. **식단 도메인 구현**
   - [ ] `FoodCatalog` 엔티티
   - [ ] `Meal` 엔티티
   - [ ] `MealItem` 엔티티
   - [ ] `FoodCatalogRepository`
   - [ ] `MealRepository`
   - [ ] `MealItemRepository`

4. **비즈니스 로직 구현**
   - [ ] `FoodSearchService`
     - Redis 캐시 확인 (30일 TTL)
     - PostgreSQL 로컬 캐시 확인
     - USDA API 호출 (기본 재료)
     - Open Food Facts API 호출 (가공식품, 바코드)
     - 검색 결과 캐싱
   - [ ] `MealService`
     - 식사 생성, 조회, 수정, 삭제
     - 식사 항목 추가/삭제
     - 영양소 자동 계산
   - [ ] `DietSummaryService`
     - 일간 매크로 집계 (칼로리, 단백질, 탄수화물, 지방)
     - 주간 평균
     - Redis 캐싱 (당일 종료 시까지 TTL)

5. **API 엔드포인트 구현**
   - [ ] `GET /api/v1/diet/food/search` (음식 검색)
   - [ ] `GET /api/v1/diet/food/barcode/{barcode}` (바코드 조회)
   - [ ] `POST /api/v1/diet/meals` (식사 생성)
   - [ ] `GET /api/v1/diet/meals` (식사 목록 조회)
   - [ ] `GET /api/v1/diet/meals/{id}` (식사 상세 조회)
   - [ ] `PATCH /api/v1/diet/meals/{id}` (식사 수정)
   - [ ] `DELETE /api/v1/diet/meals/{id}` (식사 삭제)
   - [ ] `POST /api/v1/diet/meals/{id}/items` (식사 항목 추가)
   - [ ] `DELETE /api/v1/diet/meals/{id}/items/{itemId}` (항목 삭제)
   - [ ] `GET /api/v1/diet/summary/daily` (일간 요약)
   - [ ] `GET /api/v1/diet/summary/weekly` (주간 요약)

6. **테스트**
   - [ ] USDA API 클라이언트 테스트 (실제 API 호출)
   - [ ] Open Food Facts API 테스트
   - [ ] 계층적 검색 로직 테스트
   - [ ] 영양소 계산 테스트
   - [ ] 캐싱 동작 테스트

#### iOS 앱 작업

1. **데이터 모델**
   - [ ] `Food.swift` (음식 정보)
   - [ ] `Meal.swift` (식사)
   - [ ] `MealItem.swift` (식사 항목)
   - [ ] `NutritionSummary.swift` (영양 요약)

2. **네트워크 계층**
   - [ ] `FoodAPI.swift`
     - `searchFood(query:)`
     - `getFoodByBarcode(_:)`
   - [ ] `MealAPI.swift`
     - `createMeal(_:)`
     - `getMeals(date:)`
     - `updateMeal(id:_:)`
     - `deleteMeal(id:)`
     - `addMealItem(mealId:_:)`
     - `deleteMealItem(mealId:itemId:)`
     - `getDailySummary(date:)`
     - `getWeeklySummary(startDate:)`

3. **화면 구현 (SwiftUI/UIKit)**
   - [ ] `DietHomeView.swift` (식단 기록 홈)
   - [ ] `FoodSearchView.swift` (음식 검색)
     - SearchBar with debounce (0.5초)
     - 검색 결과 리스트
     - 즐겨찾기/최근 음식 탭
   - [ ] `BarcodeScannerView.swift` (바코드 스캔)
     - AVFoundation 카메라 통합
     - 바코드 감지 및 API 호출
   - [ ] `MealInputView.swift` (식사 입력)
     - 식사 시간대 선택 (아침/점심/저녁/간식)
     - 음식 추가/삭제
     - 양 조절 (스테퍼 또는 텍스트 입력)
   - [ ] `MealDetailView.swift` (식사 상세)
   - [ ] `NutritionDashboardView.swift` (영양 대시보드)
     - 칼로리 진행 링/원형 차트
     - 매크로 진행 바 (단백질, 탄수화물, 지방)
     - 일간/주간 탭 전환

4. **주요 기능 구현**
   - [ ] 카메라 권한 요청 (Info.plist)
   - [ ] AVCaptureSession 바코드 스캔
   - [ ] 음식 검색 디바운싱 (Combine)
   - [ ] 즐겨찾기 로컬 저장 (UserDefaults 또는 CoreData)
   - [ ] 최근 음식 히스토리 (최대 20개)
   - [ ] 실시간 매크로 계산 및 시각화

5. **ViewModel**
   - [ ] `FoodSearchViewModel.swift`
   - [ ] `MealViewModel.swift`
   - [ ] `BarcodeScannerViewModel.swift`
   - [ ] `NutritionViewModel.swift`

#### 검증 기준

- ✅ 사용자가 음식을 검색하고 선택할 수 있다
- ✅ 바코드 스캔으로 가공식품을 추가할 수 있다
- ✅ 일간 칼로리와 매크로 목표 대비 진행 상황을 볼 수 있다
- ✅ 한국 음식(김치, 불고기 등)을 검색할 수 있다
- ✅ 검색 결과가 캐싱되어 2번째 검색 시 즉시 표시된다
- ✅ 주간 평균 영양소 섭취를 확인할 수 있다

---

### Phase 4: 신체 측정 및 진행 사진

**목표:** 사용자가 체중, 둘레, 진행 사진을 기록하고 비교할 수 있다

**PRD 연결:** 섹션 8.3 (신체 측정), 섹션 9.3 (신체 변화 대시보드)

#### 백엔드 작업

1. **DB 스키마 구현**
   - [ ] `body_measurements` 테이블
   - [ ] `progress_photos` 테이블

2. **S3 및 이미지 처리 구현**
   - [ ] `S3StorageService.java`
     - S3 업로드 (멀티파트)
     - 서명된 URL 생성 (15분 TTL)
     - 파일 삭제
   - [ ] `PhotoProcessingService.java`
     - EXIF 제거 (GPS, 기기 정보)
     - 썸네일 생성 (150px, 400px, 800px)
     - 비동기 처리 (@Async)

3. **측정 도메인 구현**
   - [ ] `BodyMeasurement` 엔티티
   - [ ] `ProgressPhoto` 엔티티
   - [ ] `BodyMeasurementRepository`
   - [ ] `ProgressPhotoRepository`

4. **비즈니스 로직 구현**
   - [ ] `BodyMeasurementService`
     - 측정 기록 생성, 조회
     - BMI, WHR 자동 계산
     - US Navy 체지방 공식 적용
     - 히스토리 조회 (추세 그래프용)
   - [ ] `ProgressPhotoService`
     - 사진 업로드 (EXIF 제거 → S3 저장)
     - 썸네일 생성
     - 서명된 URL 발급
     - 기준(baseline) 사진 설정
     - 사진 비교 데이터 생성

5. **API 엔드포인트 구현**
   - [ ] `POST /api/v1/measurements` (측정 기록)
   - [ ] `GET /api/v1/measurements/history` (측정 히스토리)
   - [ ] `POST /api/v1/measurements/photos` (진행 사진 업로드)
   - [ ] `GET /api/v1/measurements/photos` (사진 목록 조회)
   - [ ] `GET /api/v1/measurements/photos/{id}` (사진 상세 조회, 서명된 URL 포함)
   - [ ] `DELETE /api/v1/measurements/photos/{id}` (사진 삭제)
   - [ ] `PATCH /api/v1/measurements/photos/{id}/baseline` (기준 사진 설정)

6. **테스트**
   - [ ] BMI, WHR, US Navy 공식 계산 테스트
   - [ ] EXIF 제거 테스트
   - [ ] 썸네일 생성 테스트
   - [ ] S3 업로드/삭제 테스트 (LocalStack)
   - [ ] 서명된 URL 생성 및 만료 테스트

#### iOS 앱 작업

1. **데이터 모델**
   - [ ] `BodyMeasurement.swift` (신체 측정 데이터)
   - [ ] `ProgressPhoto.swift` (진행 사진 정보)
   - [ ] `MeasurementHistory.swift` (히스토리)

2. **네트워크 계층**
   - [ ] `MeasurementAPI.swift`
     - `createMeasurement(_:)`
     - `getMeasurementHistory(startDate:endDate:)`
   - [ ] `PhotoAPI.swift`
     - `uploadPhoto(image:type:metadata:)`
     - `getPhotos()`
     - `getPhoto(id:)`
     - `deletePhoto(id:)`
     - `setBaseline(id:)`

3. **화면 구현 (SwiftUI/UIKit)**
   - [ ] `MeasurementInputView.swift` (측정 입력)
     - 체중, 허리, 엉덩이, 목, 팔, 허벅지, 종아리 입력
     - NumberField with decimal keyboard
     - 실시간 BMI, WHR, 체지방률 계산 표시
   - [ ] `MeasurementHistoryView.swift` (히스토리)
     - Charts 프레임워크 사용 (iOS 16+)
     - 체중 추세선 그래프
     - WHR, 체지방률 추세
   - [ ] `PhotoCaptureView.swift` (사진 촬영)
     - AVFoundation 카메라 컨트롤
     - 4가지 포즈 선택 (Segmented Control)
     - 포즈 가이드 오버레이 (반투명 실루엣)
     - 타이머 (3초 카운트다운)
   - [ ] `PhotoGalleryView.swift` (사진 갤러리)
     - Grid layout (LazyVGrid)
     - 날짜별 정렬
   - [ ] `PhotoComparisonView.swift` (사진 비교)
     - 좌우 슬라이더 (custom gesture)
     - 기준 사진 vs 선택 사진
     - 측정값 변화 표시

4. **주요 기능 구현**
   - [ ] 카메라 권한 요청
   - [ ] AVCaptureSession 사진 촬영
   - [ ] 클라이언트 측 EXIF 제거
     - CGImageSource로 메타데이터 제거
     - GPS, 기기 정보 삭제
   - [ ] 이미지 리사이징 (업로드 전 압축)
   - [ ] 멀티파트 업로드 (Alamofire)
   - [ ] Kingfisher로 서명된 URL 이미지 로딩
   - [ ] BMI, WHR, US Navy 공식 계산 로직

5. **ViewModel**
   - [ ] `MeasurementViewModel.swift`
   - [ ] `PhotoViewModel.swift`
   - [ ] `CameraViewModel.swift`

#### 검증 기준

- ✅ 사용자가 체중과 둘레를 기록할 수 있다
- ✅ BMI, WHR, 체지방률이 자동 계산된다
- ✅ 사용자가 4가지 포즈로 진행 사진을 촬영할 수 있다
- ✅ EXIF 데이터가 제거되어 업로드된다
- ✅ 기준 사진과 최근 사진을 비교할 수 있다
- ✅ 측정값 추세 그래프를 확인할 수 있다

---

### Phase 5: 목표 및 인사이트

**목표:** 사용자가 목표를 설정하고 진행 상황을 추적할 수 있다

**PRD 연결:** 섹션 8.4 (목표 설정), 섹션 9.4 (목표 진행 대시보드), 섹션 10 (인사이트)

#### 백엔드 작업

1. **DB 스키마 구현**
   - [ ] `goals` 테이블
   - [ ] `goal_checkpoints` 테이블

2. **목표 도메인 구현**
   - [ ] `Goal` 엔티티
   - [ ] `GoalCheckpoint` 엔티티
   - [ ] `GoalRepository`
   - [ ] `GoalCheckpointRepository`

3. **비즈니스 로직 구현**
   - [ ] `GoalService`
     - 목표 생성 (목표 타입, 목표값, 기한)
     - 칼로리 목표 자동 계산 (Mifflin-St Jeor)
     - 주간 변화율 권장
     - 목표 상태 업데이트 (ACTIVE/COMPLETED/ABANDONED)
   - [ ] `GoalProgressService`
     - 주간 체크포인트 생성 (매주 일요일 자동)
     - 실제값 vs 예상값 비교
     - 선형 예측 (목표 달성 예상일)
     - On-track 여부 판단

4. **인사이트 로직 구현**
   - [ ] `InsightEngine.java`
     - 주간 칼로리 평균 vs 목표 비교
     - 운동량 추세 분석
     - 체중 변화율 vs 목표 변화율
     - 매크로 균형 평가
     - 기록 일관성 평가 (주 X회 기록 달성 여부)

5. **API 엔드포인트 구현**
   - [ ] `POST /api/v1/goals` (목표 생성)
   - [ ] `GET /api/v1/goals` (목표 목록 조회)
   - [ ] `GET /api/v1/goals/{id}` (목표 상세 조회)
   - [ ] `PATCH /api/v1/goals/{id}` (목표 수정)
   - [ ] `DELETE /api/v1/goals/{id}` (목표 삭제)
   - [ ] `GET /api/v1/goals/{id}/progress` (진행 상황 조회)
   - [ ] `GET /api/v1/insights/weekly` (주간 인사이트)

6. **테스트**
   - [ ] Mifflin-St Jeor BMR 계산 테스트
   - [ ] 주간 체크포인트 생성 테스트
   - [ ] 선형 예측 알고리즘 테스트
   - [ ] 인사이트 로직 테스트

#### iOS 앱 작업

1. **데이터 모델**
   - [ ] `Goal.swift` (목표 정보)
   - [ ] `GoalProgress.swift` (진행 상황)
   - [ ] `Checkpoint.swift` (체크포인트)
   - [ ] `Insight.swift` (인사이트 카드)

2. **네트워크 계층**
   - [ ] `GoalAPI.swift`
     - `createGoal(_:)`
     - `getGoals()`
     - `getGoal(id:)`
     - `updateGoal(id:_:)`
     - `deleteGoal(id:)`
     - `getProgress(id:)`
   - [ ] `InsightAPI.swift`
     - `getWeeklyInsights()`

3. **화면 구현 (SwiftUI/UIKit)**
   - [ ] `GoalSetupView.swift` (목표 설정)
     - 목표 타입 Picker (체중 감량/근육 증가/체지방 감소)
     - 목표값 입력 (NumberField)
     - DatePicker (목표 기한)
     - 주간 변화율 자동 계산 및 권장 표시
   - [ ] `GoalDashboardView.swift` (목표 대시보드)
     - 링 차트 (현재값 vs 목표값)
     - 진행률 % (ProgressView)
     - 예상 완료일 표시
     - On-track 상태 (✅/⚠️/❌)
     - 체크포인트 그래프
   - [ ] `WeeklyReviewView.swift` (주간 회고)
     - 인사이트 카드 리스트
     - 카드 타입별 색상 (긍정=초록, 중립=파랑, 개선=주황)
     - 다음 주 조언 섹션
   - [ ] `MainDashboardView.swift` (메인 대시보드)
     - TabView 루트
     - 오늘의 요약 (칼로리, 운동, 목표 진행률)
     - 빠른 기록 버튼들

4. **주요 기능 구현**
   - [ ] 목표 진행률 애니메이션 (ProgressView animation)
   - [ ] Charts 프레임워크 (체크포인트 히스토리)
   - [ ] 로컬 푸시 알림 (주간 회고)
     - UNUserNotificationCenter
     - 일요일 20:00 예약
   - [ ] 인사이트 카드 레이아웃

5. **ViewModel**
   - [ ] `GoalViewModel.swift`
   - [ ] `InsightViewModel.swift`
   - [ ] `DashboardViewModel.swift`

#### 검증 기준

- ✅ 사용자가 목표를 설정할 수 있다
- ✅ 목표에 따라 칼로리 목표가 자동 계산된다
- ✅ 주간 체크포인트가 자동 생성된다
- ✅ 진행률과 예상 완료일이 표시된다
- ✅ 주간 인사이트가 제공된다
- ✅ On-track 상태를 확인할 수 있다

---

### Phase 6: MVP 출시 준비

**목표:** 프로덕션 배포 및 초기 사용자 테스트

#### 백엔드 작업

1. **프로덕션 인프라 구성**
   - [ ] AWS 계정 설정 (ap-northeast-2 Seoul)
   - [ ] RDS PostgreSQL 생성 (Multi-AZ)
   - [ ] ElastiCache Redis 생성
   - [ ] S3 버킷 생성 (진행 사진용)
     - 서버 측 암호화 활성화
     - Cross-region replication (Osaka)
   - [ ] EC2 또는 ECS 설정
   - [ ] ALB 설정 (HTTPS, SSL 인증서)
   - [ ] Route 53 DNS 설정

2. **보안 강화**
   - [ ] Secrets Manager에 환경 변수 저장
   - [ ] CORS 정책 최종 확인
   - [ ] Rate limiting 설정 (Spring Cloud Gateway 또는 Bucket4j)
   - [ ] SQL Injection 방어 확인 (PreparedStatement 사용)
   - [ ] XSS 방어 확인

3. **모니터링 및 로깅**
   - [ ] CloudWatch 로그 설정
   - [ ] 애플리케이션 로그 레벨 조정 (INFO)
   - [ ] Health check endpoint 확인
   - [ ] 주요 지표 모니터링 (API 응답 시간, 에러율)

4. **데이터베이스 마이그레이션**
   - [ ] 프로덕션 DB 초기화
   - [ ] 운동 카탈로그 시딩 (800개 운동, MET 값)
   - [ ] 한국 음식 시딩 (100개 이상)

5. **FCM 설정**
   - [ ] Firebase 프로젝트 생성
   - [ ] FCM 서버 키 발급
   - [ ] 백엔드에 FCM Admin SDK 통합
   - [ ] 푸시 알림 테스트

#### 모바일 작업

1. **앱 스토어 준비**
   - [ ] 앱 아이콘 디자인
   - [ ] 스플래시 스크린
   - [ ] 온보딩 화면 (첫 실행 시)
   - [ ] 앱 설명, 스크린샷 준비
   - [ ] 개인정보 처리방침 URL
   - [ ] 이용약관 URL

2. **성능 최적화**
   - [ ] 이미지 최적화
   - [ ] API 호출 최소화 (캐싱)
   - [ ] 메모리 누수 확인

3. **디바이스 테스트**
   - [ ] 다양한 화면 크기 테스트
   - [ ] iOS 16 이상 테스트
   - [ ] Android 10 이상 테스트

4. **배포**
   - [ ] TestFlight 또는 Google Play 내부 테스트 배포
   - [ ] 초기 테스터 모집 (5-10명)
   - [ ] 피드백 수집 및 버그 수정

#### 문서화

1. **기술 문서**
   - [ ] API 문서 (Swagger/OpenAPI)
   - [ ] 배포 가이드
   - [ ] 환경 변수 목록
   - [ ] 트러블슈팅 가이드

2. **사용자 문서**
   - [ ] 개인정보 처리방침
   - [ ] 이용약관
   - [ ] FAQ
   - [ ] 앱 사용 가이드

#### 검증 기준

- ✅ 프로덕션 환경에서 모든 기능 동작 확인
- ✅ HTTPS 통신 확인
- ✅ FCM 푸시 알림 전송 성공
- ✅ 5-10명 테스터가 E2E 시나리오 완료
- ✅ 주요 버그 없음 (P0/P1 버그 0건)
- ✅ API 응답 시간 목표 달성 (PRD 섹션 7.1)
  - 기록 API < 500ms
  - 검색 API (캐시) < 300ms
  - 사진 업로드 < 5초

---

## 3. 도메인별 구현 우선순위

### 3.1 도메인 의존성 그래프

```
User/Auth (Phase 1)
    │
    ├─→ Exercise (Phase 2)
    │       └─→ Goal (Phase 5)
    │
    ├─→ Diet (Phase 3)
    │       └─→ Goal (Phase 5)
    │
    └─→ Measurement (Phase 4)
            └─→ Goal (Phase 5)
```

### 3.2 우선순위 근거

| 순위 | 도메인 | 근거 |
|-----|-------|------|
| 1 | User/Auth | 모든 기능의 전제 조건 |
| 2 | Exercise | 외부 API 불필요, 로직 단순, 빠른 검증 가능 |
| 3 | Diet | USDA/OFF API 연동 복잡도, 한국 음식 데이터 확보 필요 |
| 4 | Measurement | S3, 이미지 처리 추가 작업 필요 |
| 5 | Goal | 다른 도메인 데이터 필요 (운동, 식단, 측정) |

---

## 4. 기술 스택별 작업 흐름

### 4.1 백엔드 (Spring Boot) 작업 패턴

각 도메인 구현 시 다음 순서를 따름:

```
1. DB 스키마 작성 (Flyway/Liquibase)
    ↓
2. 엔티티 클래스 작성
    ↓
3. Repository 인터페이스 작성
    ↓
4. Service 계층 구현 (비즈니스 로직)
    ↓
5. Controller 작성 (API 엔드포인트)
    ↓
6. DTO 클래스 작성 (Request/Response)
    ↓
7. 단위 테스트 작성 (Service)
    ↓
8. 통합 테스트 작성 (Controller)
    ↓
9. API 문서 업데이트 (Swagger)
```

### 4.2 iOS 앱 작업 패턴 (Swift)

각 기능 구현 시:

```
1. 데이터 모델 작성 (Codable structs)
    ↓
2. API 클라이언트 함수 작성 (Alamofire)
    ↓
3. 화면 UI 작성 (SwiftUI View 또는 UIViewController)
    ↓
4. ViewModel 작성 (@Published 프로퍼티, 비즈니스 로직)
    ↓
5. API 연동 (async/await, 로딩/에러 상태)
    ↓
6. UI 바인딩 (@StateObject, @ObservedObject)
    ↓
7. UI 동작 확인 (시뮬레이터 + 실제 기기)
    ↓
8. 엣지 케이스 처리 (네트워크 끊김, 에러 핸들링)
```

**iOS 개발 권장 사항:**
- **UI 프레임워크:** SwiftUI 우선 (iOS 16+ 지원), 복잡한 UI는 UIKit
- **네트워킹:** Alamofire (타입 안전, 인터셉터)
- **이미지 로딩:** Kingfisher (캐싱, 서명된 URL 지원)
- **상태 관리:** SwiftUI MVVM 패턴 (ViewModel + Combine)
- **로컬 저장소:** Keychain (토큰), UserDefaults (설정), CoreData (오프라인 캐싱 - Phase 7 이후)

### 4.3 병렬 작업 가능 시점

**Phase 2-5 동안 백엔드와 iOS 앱 작업 병렬화 가능:**

```
Week 1-2: 백엔드 Phase 2 (운동 기록 API)
Week 3-4: 백엔드 Phase 3 (식단 기록 API) || iOS Phase 2 (운동 화면)
Week 5-6: 백엔드 Phase 4 (측정 API) || iOS Phase 3 (식단 화면)
Week 7-8: 백엔드 Phase 5 (목표 API) || iOS Phase 4 (측정 화면)
Week 9-10: iOS Phase 5 (목표 화면) + iOS-백엔드 통합 테스트
Week 11-12: MVP 출시 준비 (TestFlight 배포, 초기 테스터)
```

**Solo 개발 시 권장 순서:**
1. 백엔드 Phase 0-1 완성 (인증 API)
2. iOS Phase 0-1 완성 (로그인 화면)
3. 백엔드 Phase 2 → iOS Phase 2 (순차)
4. 이후 동일 패턴 반복

---

## 5. 검증 및 테스트 전략

### 5.1 테스트 레벨

**단위 테스트 (JUnit 5):**
- 모든 계산 로직 (칼로리, BMI, WHR 등)
- 비즈니스 로직 (PR 감지, 목표 진행률 등)
- 유틸리티 함수

**통합 테스트 (Spring Boot Test):**
- API 엔드포인트 (Controller)
- DB 연동 (Repository)
- Redis 캐싱
- 외부 API 호출 (MockServer)

**E2E 테스트 (수동):**
- 주요 사용자 시나리오
  1. 회원가입 → 목표 설정 → 운동 기록 → 대시보드 확인
  2. 식단 기록 → 바코드 스캔 → 영양소 확인
  3. 진행 사진 촬영 → 비교 → 주간 회고

### 5.2 테스트 커버리지 목표

- **핵심 비즈니스 로직:** 80% 이상
- **Controller:** 70% 이상
- **전체 프로젝트:** 60% 이상

### 5.3 Phase별 검증 체크리스트

각 Phase 완료 시:

- [ ] 모든 단위 테스트 통과
- [ ] 모든 통합 테스트 통과
- [ ] API 응답 시간 목표 달성
- [ ] 에러 처리 확인 (400, 401, 404, 500)
- [ ] 로그 레벨 적절성 확인
- [ ] 메모리 누수 없음
- [ ] 모바일 앱 크래시 없음

---

## 6. 배포 및 출시 준비

### 6.1 배포 전략

**로컬 개발 (Phase 0-5):**
- Docker Compose 사용
- LocalStack (S3)
- Mock FCM

**개발/스테이징 환경 (Phase 2-5):**
- AWS EC2 (t3.small)
- RDS PostgreSQL (db.t3.micro)
- ElastiCache Redis (cache.t3.micro)
- S3

**프로덕션 환경 (Phase 6):**
- AWS EC2 Auto Scaling (t3.medium)
- RDS PostgreSQL Multi-AZ (db.t3.medium)
- ElastiCache Redis (cache.t3.micro)
- S3 + CloudFront
- ALB (HTTPS)

### 6.2 CI/CD 파이프라인

**백엔드:**
```
GitHub Push
    ↓
GitHub Actions
    ├─→ Build (Gradle)
    ├─→ Test (JUnit)
    ├─→ Code Quality (SonarQube - optional)
    └─→ Docker Image Build
         ↓
    Push to ECR
         ↓
    Deploy to EC2/ECS
```

**모바일:**
```
GitHub Push
    ↓
GitHub Actions (or Fastlane)
    ├─→ Build
    ├─→ Test
    └─→ Deploy to TestFlight/Google Play Internal
```

### 6.3 출시 체크리스트

**기술적 준비:**
- [ ] 프로덕션 DB 백업 설정
- [ ] 모니터링 대시보드 설정
- [ ] 에러 알림 설정 (Slack, Email)
- [ ] 로그 보관 정책 설정
- [ ] SSL 인증서 설정 및 자동 갱신

**법적/정책 준비:**
- [ ] 개인정보 처리방침 작성 및 게시
- [ ] 이용약관 작성 및 게시
- [ ] 앱 스토어 개인정보 보호 설문 작성
- [ ] 데이터 보관/삭제 정책 확인

**마케팅 준비:**
- [ ] 앱 스토어 설명 작성 (한국어, 영어)
- [ ] 스크린샷 준비 (각 5장)
- [ ] 프로모션 비디오 (선택)
- [ ] 랜딩 페이지 (선택)

---

## 7. 위험 요소 및 완화 전략

### 7.1 주요 위험

| 위험 | 영향도 | 완화 전략 |
|-----|-------|---------|
| **한국 음식 데이터 부족** | 높음 | Open Food Facts 커뮤니티 기여 독려, 초기 100개 수동 시딩, 사용자 직접 입력 기능 |
| **외부 API 속도 제한** | 중간 | 30일 캐싱 전략, 로컬 DB 우선 검색, Rate limit 모니터링 |
| **사진 업로드 대역폭** | 중간 | 클라이언트 측 이미지 압축, 썸네일 먼저 표시, 백그라운드 업로드 |
| **FCM 전송 실패** | 낮음 | 재시도 로직, 실패 로그 모니터링, fallback to in-app notification |
| **iOS 개발 복잡도** | 중간 | Xcode, Swift 숙련도 향상, 오픈소스 라이브러리 활용 |
| **Android 추가 개발 시간** | 중간 | iOS 로직 재사용, Kotlin 공통 아키텍처 적용 (Phase 7) |

### 7.2 기술 부채 모니터링

**매 Phase 종료 시 확인:**
- 하드코딩된 값이 설정 파일로 이동했는가?
- 중복 코드가 공통 함수로 추출되었는가?
- TODO 주석이 이슈 트래커에 등록되었는가?
- 테스트 커버리지가 목표치를 유지하는가?

---

## 8. 다음 단계 (MVP 이후)

### 8.1 Phase 7: Android 앱 추가 (v1.2)

**목표:** iOS MVP 출시 후 Android 사용자 확장

**예상 기간:** 6-8주

#### 작업 내용

1. **Android 프로젝트 초기화**
   - Android Studio에서 새 프로젝트 생성
   - Kotlin + Jetpack Compose
   - Package name: com.healthcare.app
   - Min SDK: API 29 (Android 10)

2. **iOS 로직 이식**
   - API 클라이언트 (Retrofit + OkHttp)
   - 데이터 모델 (Kotlin data class)
   - ViewModel (Android Architecture Components)
   - 화면 UI (Jetpack Compose)

3. **Android 특화 기능**
   - Keystore에 토큰 저장
   - CameraX로 바코드 스캔, 사진 촬영
   - WorkManager로 백그라운드 동기화
   - FCM 푸시 알림 (Android 채널 설정)

4. **공통 로직 재사용**
   - 비즈니스 로직은 iOS와 동일
   - API 엔드포인트 동일
   - 백엔드 변경 불필요

**검증 기준:**
- ✅ iOS 앱과 기능 동등성 (feature parity)
- ✅ Android 10-14 호환성
- ✅ Google Play Console 제출

---

### 8.2 Phase 8: 웹 대시보드 (v1.5)

- 읽기 전용 대시보드
- 추세 분석 강화
- 데이터 내보내기 (CSV, PDF)
- 목표 설정만 웹에서 가능

---

### 8.3 Phase 9: 소셜 기능 (v2.0)

- 친구 추가
- 운동 챌린지
- 리더보드
- 공개 프로필

---

### 8.4 Phase 10: AI 추천 (v2.5)

- 운동 루틴 추천
- 식단 제안
- 목표 달성 예측 강화

---

## 부록 A: 체크리스트 요약

### Phase 0: 환경 구축
- [ ] Spring Boot 프로젝트 초기화 (Java 21)
- [ ] Docker Compose 설정 (PostgreSQL, Redis, LocalStack)
- [ ] Xcode iOS 프로젝트 생성 (Swift)
- [ ] Health Check API 동작 확인

### Phase 1: 인증
- [ ] JWT 인증 구현 (백엔드)
- [ ] 회원가입/로그인 API
- [ ] iOS 로그인 화면 (SwiftUI)
- [ ] Keychain 토큰 저장
- [ ] 토큰 갱신 자동화

### Phase 2: 운동 기록
- [ ] 운동 카탈로그 API (800개 MET 시딩)
- [ ] 운동 세션 CRUD API
- [ ] MET 칼로리 계산
- [ ] PR 감지 로직
- [ ] iOS 운동 기록 화면 (타이머, 세트 입력)

### Phase 3: 식단 기록
- [ ] USDA API 클라이언트
- [ ] Open Food Facts API 클라이언트
- [ ] 계층적 검색 로직 (30일 캐싱)
- [ ] 식사 CRUD API
- [ ] iOS 바코드 스캔 화면 (AVFoundation)

### Phase 4: 신체 측정
- [ ] S3 통합 (이미지 업로드)
- [ ] EXIF 제거 (서버+클라이언트)
- [ ] 썸네일 생성 (3가지 크기)
- [ ] 측정 기록 API
- [ ] iOS 사진 촬영 화면 (4가지 포즈)

### Phase 5: 목표
- [ ] 목표 CRUD API
- [ ] 주간 체크포인트 자동 생성
- [ ] 인사이트 엔진
- [ ] iOS 목표 대시보드 (Charts 프레임워크)

### Phase 6: iOS MVP 출시
- [ ] AWS 인프라 구성 (Seoul 리전)
- [ ] FCM 설정 (iOS 푸시)
- [ ] TestFlight 배포
- [ ] App Store 제출
- [ ] 초기 테스터 피드백 (5-10명)

### Phase 7: Android 앱 추가 (MVP 이후)
- [ ] Android Studio 프로젝트 (Kotlin + Jetpack Compose)
- [ ] iOS 로직 이식 (Retrofit, CameraX)
- [ ] Google Play Console 제출

---

**문서 종료**