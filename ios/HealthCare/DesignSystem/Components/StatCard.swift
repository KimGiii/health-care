import SwiftUI

/// Editorial stat card — eyebrow + large rounded numeral + unit.
/// Uses a thin hairline and subtle warm paper background.
struct StatCard: View {
    let title: String
    let value: String
    let unit: String
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.eyebrow)
                .tracking(1.8)
                .textCase(.uppercase)
                .foregroundStyle(Color.textTertiary)

            HStack(alignment: .lastTextBaseline, spacing: 4) {
                Text(value)
                    .font(.numeralLarge)
                    .foregroundStyle(color)
                Text(unit)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color.textSecondary)
            }

            // subtle hairline accent
            Rectangle()
                .fill(color.opacity(0.35))
                .frame(width: 22, height: 2)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(Color.brandDusk.opacity(0.06), lineWidth: 1)
                )
        )
        .elevation(.low)
    }
}
