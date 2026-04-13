# HealthCare

운동·식단·신체변화를 하나로 기록하고 목표를 달성하는 iOS 헬스 트래킹 앱

## 기술 스택

**Backend**
- Java 21 + Spring Boot 3.3.4
- PostgreSQL 16 + Redis 7
- JWT (Access Token 24h / Refresh Token 30d)
- Flyway, Docker Compose

**iOS**
- Swift 6.0 + SwiftUI
- MVVM, strict concurrency (actor model)
- Keychain 토큰 저장
- iOS 16+

## 프로젝트 구조

```
health-care/
├── backend/          # Spring Boot API 서버
├── ios/              # SwiftUI 클라이언트
├── docs/             # 설계 문서 (PRD, API, DB 스키마)
└── research/         # 경쟁사 분석, 기술 벤치마크
```

## 문서

- [아키텍처](./ARCHITECTURE.md)
- [API 설계](./docs/API_DESIGN.md)
- [DB 스키마](./docs/DB_SCHEMA.md)
- [PRD](./docs/design-docs/PRD.md)
