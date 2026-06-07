import CoreGraphics

// MARK: - Spacing Scale (4pt base)
//
// 화면에서 padding/spacing 값을 직접 박지 말고 이 토큰을 사용한다.
// 매직 넘버(`.padding(.horizontal, 16)`) 사용 시 PR lint에서 차단된다.
//
// 규칙:
//   - 베이스: 4pt
//   - 카드/섹션 내부 여백 = .md / .lg
//   - 화면 좌우 여백 = .lg (기본) 또는 .xl (히어로 카드)
//   - 카드 간 갭 = .md
//   - 텍스트 라인간 미세 갭 = .xs / .sm

enum Spacing {
    /// 4pt — 라벨/아이콘 미세 갭
    static let xs:  CGFloat = 4
    /// 8pt — 인접 요소(아이콘 + 텍스트, 칩 내부)
    static let sm:  CGFloat = 8
    /// 12pt — 카드 간격, 텍스트 블록 사이
    static let md:  CGFloat = 12
    /// 16pt — 카드 내부 패딩, 폼 필드 간격
    static let lg:  CGFloat = 16
    /// 20pt — 화면 좌우 가터(기본)
    static let xl:  CGFloat = 20
    /// 28pt — 섹션 사이 큰 호흡, 히어로 가터
    static let xxl: CGFloat = 28
    /// 40pt — 화면 상단 영역, 모달 헤더
    static let xxxl: CGFloat = 40
}

// MARK: - Corner Radius Scale
//
// cornerRadius 매직 넘버(8/10/12/14/18 등) 금지. 아래 4단계 + 1특수(.pill).
//
// 규칙:
//   - 칩/태그/작은 버튼 = .sm
//   - 폼 입력 = .md
//   - 카드(기본) = .lg
//   - 히어로 카드/모달 시트 = .xl
//   - 캡슐(완전 라운드) = .pill

enum Radius {
    /// 8pt — 칩, 태그, 작은 버튼
    static let sm:  CGFloat = 8
    /// 12pt — 폼 입력 필드, 토글
    static let md:  CGFloat = 12
    /// 16pt — 카드(기본), 모달
    static let lg:  CGFloat = 16
    /// 22pt — 히어로 카드, 큰 컨테이너
    static let xl:  CGFloat = 22
    /// 완전 라운드 (Capsule용 sentinel — `Capsule()` 사용 권장)
    static let pill: CGFloat = .infinity
}
