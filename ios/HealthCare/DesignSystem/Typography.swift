import SwiftUI

// MARK: - Type Scale
//
// VITALITY uses an editorial scale with deliberate contrast.
// Display = serif-feel rounded (for emotional numerals & hero moments).
// Heading = system bold (for section titles).
// Body    = system regular (legibility).
// Mono    = monospaced digits (data readouts).

extension Font {
    // Display — extra-large editorial moments
    static let displayHero   = Font.system(size: 56, weight: .heavy,   design: .serif)
    static let displayLarge  = Font.system(size: 40, weight: .bold,    design: .serif)
    static let displayMedium = Font.system(size: 30, weight: .bold,    design: .serif)

    // Numerals — rounded, for ring labels and big stats
    static let numeralHero   = Font.system(size: 44, weight: .heavy,   design: .rounded)
    static let numeralLarge  = Font.system(size: 28, weight: .bold,    design: .rounded)
    static let numeralMedium = Font.system(size: 20, weight: .semibold, design: .rounded)

    // Heading — UI labels
    static let headingLarge   = Font.system(size: 22, weight: .bold)
    static let headingMedium  = Font.system(size: 18, weight: .semibold)
    static let headingSmall   = Font.system(size: 15, weight: .semibold)

    // Body
    static let bodyLarge      = Font.system(size: 17, weight: .regular)
    static let bodyMedium     = Font.system(size: 15, weight: .regular)
    static let bodySmall      = Font.system(size: 13, weight: .regular)

    // Caption / eyebrow
    static let eyebrow        = Font.system(size: 11, weight: .heavy).monospaced()
    static let caption        = Font.system(size: 12, weight: .regular)
    static let captionBold    = Font.system(size: 12, weight: .semibold)

    // Data
    static let dataSmall      = Font.system(size: 12, weight: .medium, design: .monospaced)
    static let dataMedium     = Font.system(size: 14, weight: .semibold, design: .monospaced)
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
