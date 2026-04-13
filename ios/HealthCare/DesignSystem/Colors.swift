import SwiftUI

extension Color {
    // Brand — Dark Forest Green palette
    static let brandPrimary      = Color(hex: "#1A4A2E")   // 진한 녹색 (헤더/버튼)
    static let brandSecondary    = Color(hex: "#2D6A4F")   // 중간 녹색
    static let brandAccent       = Color(hex: "#52B788")   // 밝은 민트 그린
    static let brandSurface      = Color(hex: "#D8F3DC")   // 연한 민트 배경
    static let brandLight        = Color(hex: "#F0FAF3")   // 거의 흰 민트

    // Status
    static let brandSuccess      = Color(hex: "#40916C")
    static let brandWarning      = Color(hex: "#F4A261")
    static let brandDanger       = Color(hex: "#E63946")

    // Neutral
    static let surfacePrimary    = Color(.systemBackground)
    static let surfaceSecondary  = Color(.secondarySystemBackground)
    static let surfaceGrouped    = Color(.systemGroupedBackground)
    static let textPrimary       = Color(hex: "#1A1A1A")
    static let textSecondary     = Color(hex: "#6B7280")
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
