import SwiftUI

// MARK: - Brand Palette

extension Color {
    // Brand — Dark Forest Green (deepened, richer)
    // brandPrimary: 라이트 모드에서는 딥 포레스트, 다크 모드에서는 구별 가능한 밝은 포레스트
    static let brandPrimary = Color(UIColor { t in
        t.userInterfaceStyle == .dark
            ? UIColor(adaptHex: "#1E6040")   // lighter forest — readable on dark bg
            : UIColor(adaptHex: "#0F3B24")   // deeper forest for light bg
    })
    static let brandSecondary    = Color(hex: "#2D6A4F")   // mid forest
    static let brandTertiary     = Color(hex: "#1A4A2E")   // classic forest (legacy)
    static let brandAccent       = Color(hex: "#52B788")   // fresh mint
    static let brandAccentGlow   = Color(hex: "#95E2B5")   // light mint glow
    static let brandSurface      = Color(hex: "#D8F3DC")   // mint surface
    static let brandLight        = Color(hex: "#F0FAF3")   // near-white mint

    // Editorial accents — warm counterweights to green
    static let brandSunrise      = Color(hex: "#F6C177")   // warm amber
    static let brandEmber        = Color(hex: "#E07856")   // terracotta
    static let brandMoss         = Color(hex: "#6FA287")   // desaturated sage
    static let brandDusk         = Color(hex: "#0B2A1C")   // near-black forest
    static let brandBone         = Color(hex: "#EFEAE0")   // warm off-white (light-only brand token)

    // Status
    static let brandSuccess      = Color(hex: "#40916C")
    static let brandWarning      = Color(hex: "#F4A261")
    static let brandDanger       = Color(hex: "#E63946")

    // Neutral — UIKit semantic (auto dark mode)
    static let surfacePrimary    = Color(.systemBackground)
    static let surfaceSecondary  = Color(.secondarySystemBackground)
    static let surfaceGrouped    = Color(.systemGroupedBackground)

    // MARK: Adaptive semantic tokens (light / dark)
    //
    // 다크 모드 광도 정책 (2026-05-26 조정):
    //   - 배경 ~14% / 카드 ~20% / hairline ~28% — 6%씩 elevation 차이로 카드 떠 보이게
    //   - textSecondary/Tertiary 대비를 WCAG AA(4.5:1) 이상으로 끌어올림
    //   - 브랜드 forest 톤은 유지 (그린 hue 살림)

    /// Page background — warm bone (light) / forest night (dark)
    static let backgroundPage = Color(UIColor { t in
        t.userInterfaceStyle == .dark
            ? UIColor(adaptHex: "#14241B")
            : UIColor(adaptHex: "#EFEAE0")
    })

    /// Card / elevated surface — white (light) / elevated forest (dark)
    static let surfaceCard = Color(UIColor { t in
        t.userInterfaceStyle == .dark
            ? UIColor(adaptHex: "#1E3329")
            : .white
    })

    /// Headline text — near-black forest (light) / near-white with green tint (dark)
    static let textHeadline = Color(UIColor { t in
        t.userInterfaceStyle == .dark
            ? UIColor(adaptHex: "#E4F0E8")
            : UIColor(adaptHex: "#0B2A1C")
    })

    /// Progress ring track — dark at low opacity (light) / light at low opacity (dark)
    static let ringTrack = Color(UIColor { t in
        t.userInterfaceStyle == .dark
            ? UIColor(white: 1, alpha: 0.20)
            : UIColor(adaptHex: "#0B2A1C").withAlphaComponent(0.10)
    })

    /// Card border stroke — subtle dark-on-light (light) / subtle light-on-dark (dark)
    static let cardStroke = Color(UIColor { t in
        t.userInterfaceStyle == .dark
            ? UIColor(white: 1, alpha: 0.14)
            : UIColor(adaptHex: "#0B2A1C").withAlphaComponent(0.06)
    })

    /// Body text — near-black (light) / near-white green-tinted (dark)
    static let textPrimary = Color(UIColor { t in
        t.userInterfaceStyle == .dark
            ? UIColor(adaptHex: "#ECF3EE")
            : UIColor(adaptHex: "#121815")
    })

    /// Secondary text — sage (light) / brighter sage (dark) — WCAG AA on backgroundPage
    static let textSecondary = Color(UIColor { t in
        t.userInterfaceStyle == .dark
            ? UIColor(adaptHex: "#A6C5B3")
            : UIColor(adaptHex: "#6B7A72")
    })

    /// Tertiary text — muted sage (light) / brighter sage (dark) — WCAG AA on backgroundPage
    static let textTertiary = Color(UIColor { t in
        t.userInterfaceStyle == .dark
            ? UIColor(adaptHex: "#88A797")
            : UIColor(adaptHex: "#9AA79F")
    })

    /// Hairline / divider — light warm (light) / mid forest (dark)
    static let hairline = Color(UIColor { t in
        t.userInterfaceStyle == .dark
            ? UIColor(adaptHex: "#324B3D")
            : UIColor(adaptHex: "#E3EAE4")
    })
}

// MARK: - UIColor hex helper (private)

extension UIColor {
    convenience init(adaptHex hex: String) {
        let str = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: str).scanHexInt64(&int)
        let r = CGFloat((int >> 16) & 0xFF) / 255
        let g = CGFloat((int >> 8)  & 0xFF) / 255
        let b = CGFloat(int         & 0xFF) / 255
        self.init(red: r, green: g, blue: b, alpha: 1)
    }
}

// MARK: - Semantic Gradient Tokens

extension LinearGradient {
    /// Deep forest vertical — hero backdrop.
    static let forestHero = LinearGradient(
        colors: [
            Color(hex: "#0B2A1C"),
            Color(hex: "#0F3B24"),
            Color(hex: "#164E33")
        ],
        startPoint: .top,
        endPoint: .bottom
    )

    /// Angular mint glow — used behind hero data.
    static let mintGlow = LinearGradient(
        colors: [
            Color.brandAccent.opacity(0.55),
            Color.brandAccent.opacity(0.0)
        ],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    /// Warm sunrise — accent strokes, CTA highlights.
    static let sunrise = LinearGradient(
        colors: [Color.brandSunrise, Color.brandEmber],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    /// Subtle paper tint — section backgrounds.
    static let bonePaper = LinearGradient(
        colors: [Color.brandBone, Color(hex: "#F7F3EA")],
        startPoint: .top,
        endPoint: .bottom
    )

    /// Ring fill — calorie.
    static let ringCalorie = LinearGradient(
        colors: [Color.brandAccentGlow, Color.brandAccent],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    /// Ring fill — activity.
    static let ringActivity = LinearGradient(
        colors: [Color.brandSunrise, Color.brandEmber],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )
}

// MARK: - Elevation (shadow) tokens

struct Elevation {
    let color: Color
    let radius: CGFloat
    let x: CGFloat
    let y: CGFloat

    static let low   = Elevation(color: .black.opacity(0.06), radius: 10, x: 0, y: 4)
    static let mid   = Elevation(color: .black.opacity(0.10), radius: 18, x: 0, y: 10)
    static let high  = Elevation(color: .black.opacity(0.18), radius: 32, x: 0, y: 18)
    static let forest = Elevation(color: Color(hex: "#0B2A1C").opacity(0.35), radius: 28, x: 0, y: 14)
}

extension View {
    func elevation(_ e: Elevation) -> some View {
        shadow(color: e.color, radius: e.radius, x: e.x, y: e.y)
    }
}

// MARK: - Glass tints

extension ShapeStyle where Self == Color {
    static var glassLight: Color { Color.white.opacity(0.12) }
    static var glassEdge:  Color { Color.white.opacity(0.22) }
    static var glassDeep:  Color { Color.black.opacity(0.18) }
}

// MARK: - Hex initializer

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3:
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6:
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8:
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(
            .sRGB,
            red:   Double(r) / 255,
            green: Double(g) / 255,
            blue:  Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}
