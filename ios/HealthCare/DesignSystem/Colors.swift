import SwiftUI

// MARK: - Brand Palette

extension Color {
    // Brand — Dark Forest Green (deepened, richer)
    static let brandPrimary      = Color(hex: "#0F3B24")   // deeper forest
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
    static let brandBone         = Color(hex: "#EFEAE0")   // warm off-white

    // Status
    static let brandSuccess      = Color(hex: "#40916C")
    static let brandWarning      = Color(hex: "#F4A261")
    static let brandDanger       = Color(hex: "#E63946")

    // Neutral
    static let surfacePrimary    = Color(.systemBackground)
    static let surfaceSecondary  = Color(.secondarySystemBackground)
    static let surfaceGrouped    = Color(.systemGroupedBackground)
    static let textPrimary       = Color(hex: "#121815")
    static let textSecondary     = Color(hex: "#6B7A72")
    static let textTertiary      = Color(hex: "#9AA79F")
    static let hairline          = Color(hex: "#E3EAE4")
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
