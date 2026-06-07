import SwiftUI

// MARK: - Type Scale
//
// VITALITY uses an editorial scale with deliberate contrast.
// Display = serif-feel rounded (for emotional numerals & hero moments).
// Heading = system bold (for section titles).
// Body    = system regular (legibility).
// Mono    = monospaced digits (data readouts).
//
// Dynamic Type: 모든 폰트는 UIFontMetrics를 통해 사용자의 접근성 텍스트 크기 설정에
// 비례 스케일됩니다. static let은 앱 실행 중 최초 접근 시 한 번 계산됩니다.

// MARK: - UIFont → Font 변환 헬퍼

private extension UIFont {
    /// 지정된 텍스트 스타일을 기준으로 Dynamic Type 스케일이 적용된 UIFont를 생성합니다.
    static func scaled(
        size: CGFloat,
        weight: UIFont.Weight,
        design: UIFontDescriptor.SystemDesign = .default,
        relativeTo textStyle: UIFont.TextStyle
    ) -> UIFont {
        let descriptor = UIFontDescriptor
            .preferredFontDescriptor(withTextStyle: textStyle)
            .addingAttributes([
                .traits: [
                    UIFontDescriptor.TraitKey.weight: weight
                ]
            ])

        let designDescriptor = descriptor.withDesign(design) ?? descriptor
        let baseFont = UIFont(descriptor: designDescriptor, size: size)
        return UIFontMetrics(forTextStyle: textStyle).scaledFont(for: baseFont)
    }
}

// MARK: - Font Extension (Dynamic Type 지원)

extension Font {
    // Display — extra-large editorial moments
    static let displayHero: Font = {
        let uiFont = UIFont.scaled(size: 56, weight: .heavy, design: .serif, relativeTo: .largeTitle)
        return Font(uiFont)
    }()

    static let displayLarge: Font = {
        let uiFont = UIFont.scaled(size: 40, weight: .bold, design: .serif, relativeTo: .largeTitle)
        return Font(uiFont)
    }()

    static let displayMedium: Font = {
        let uiFont = UIFont.scaled(size: 30, weight: .bold, design: .serif, relativeTo: .title1)
        return Font(uiFont)
    }()

    // Numerals — rounded, for ring labels and big stats
    static let numeralHero: Font = {
        let uiFont = UIFont.scaled(size: 44, weight: .heavy, design: .rounded, relativeTo: .largeTitle)
        return Font(uiFont)
    }()

    static let numeralLarge: Font = {
        let uiFont = UIFont.scaled(size: 28, weight: .bold, design: .rounded, relativeTo: .title1)
        return Font(uiFont)
    }()

    static let numeralMedium: Font = {
        let uiFont = UIFont.scaled(size: 20, weight: .semibold, design: .rounded, relativeTo: .title2)
        return Font(uiFont)
    }()

    // Heading — UI labels
    static let headingLarge: Font = {
        let uiFont = UIFont.scaled(size: 22, weight: .bold, relativeTo: .title2)
        return Font(uiFont)
    }()

    static let headingMedium: Font = {
        let uiFont = UIFont.scaled(size: 18, weight: .semibold, relativeTo: .title3)
        return Font(uiFont)
    }()

    static let headingSmall: Font = {
        let uiFont = UIFont.scaled(size: 15, weight: .semibold, relativeTo: .headline)
        return Font(uiFont)
    }()

    // Body
    static let bodyLarge: Font = {
        let uiFont = UIFont.scaled(size: 17, weight: .regular, relativeTo: .body)
        return Font(uiFont)
    }()

    static let bodyMedium: Font = {
        let uiFont = UIFont.scaled(size: 15, weight: .regular, relativeTo: .body)
        return Font(uiFont)
    }()

    static let bodySmall: Font = {
        let uiFont = UIFont.scaled(size: 13, weight: .regular, relativeTo: .subheadline)
        return Font(uiFont)
    }()

    // Caption / eyebrow
    // monospaced()는 Font 레벨에서 적용 (UIFont 변환 후 유지 불가)
    static let eyebrow: Font = {
        let uiFont = UIFont.scaled(size: 11, weight: .heavy, design: .monospaced, relativeTo: .caption1)
        return Font(uiFont).monospaced()
    }()

    static let caption: Font = {
        let uiFont = UIFont.scaled(size: 12, weight: .regular, relativeTo: .caption1)
        return Font(uiFont)
    }()

    static let captionBold: Font = {
        let uiFont = UIFont.scaled(size: 12, weight: .semibold, relativeTo: .caption1)
        return Font(uiFont)
    }()

    // CTA — primary/secondary 버튼 라벨 (17/semibold, Dynamic Type)
    static let cta: Font = {
        let uiFont = UIFont.scaled(size: 17, weight: .semibold, relativeTo: .headline)
        return Font(uiFont)
    }()

    // Label — 폼 필드 라벨, 토글 라벨, 마이크로 헤더 (13/semibold, Dynamic Type)
    static let labelSmall: Font = {
        let uiFont = UIFont.scaled(size: 13, weight: .semibold, relativeTo: .footnote)
        return Font(uiFont)
    }()

    // Caption XSmall — 데이터 라벨, 미니 인디케이터 (11/medium, Dynamic Type)
    // 더 작은 게 필요하면 .eyebrow(11/heavy mono) 검토.
    static let captionXSmall: Font = {
        let uiFont = UIFont.scaled(size: 11, weight: .medium, relativeTo: .caption2)
        return Font(uiFont)
    }()

    // Brand wordmark — 앱 이름/로고 옆 워드마크 (32/bold rounded, Dynamic Type)
    // 브랜드 표현 전용. 일반 헤딩에는 displayMedium / headingLarge 사용.
    static let brandWordmark: Font = {
        let uiFont = UIFont.scaled(size: 32, weight: .bold, design: .rounded, relativeTo: .largeTitle)
        return Font(uiFont)
    }()

    // Data
    static let dataSmall: Font = {
        let uiFont = UIFont.scaled(size: 12, weight: .medium, design: .monospaced, relativeTo: .caption1)
        return Font(uiFont)
    }()

    static let dataMedium: Font = {
        let uiFont = UIFont.scaled(size: 14, weight: .semibold, design: .monospaced, relativeTo: .footnote)
        return Font(uiFont)
    }()
}

// MARK: - Tracking / letter-spacing helpers

extension Text {
    /// Uppercase eyebrow label with wide tracking.
    func eyebrowStyle(_ color: Color = .textTertiary) -> some View {
        self
            .font(.eyebrow)
            .tracking(2.2)
            .textCase(.uppercase)
            .foregroundStyle(color)
    }

    /// Tight hero display tracking.
    func heroTracking() -> Text {
        self.tracking(-1.1)
    }
}
