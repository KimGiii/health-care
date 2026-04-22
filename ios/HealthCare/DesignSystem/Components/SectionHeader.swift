import SwiftUI

/// Editorial section header — orange accent rule + serif title.
/// Used across the app for consistent section rhythm.
struct SectionHeader: View {
    let title: String
    var eyebrow: String? = nil
    var actionTitle: String? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Rectangle()
                .fill(LinearGradient.sunrise)
                .frame(width: 22, height: 2)
                .offset(y: -4)

            VStack(alignment: .leading, spacing: 2) {
                if let eyebrow {
                    Text(eyebrow).eyebrowStyle(Color.textTertiary)
                }
                Text(title)
                    .font(.system(size: 20, weight: .bold, design: .serif))
                    .foregroundStyle(Color.brandDusk)
            }

            Spacer()

            if let actionTitle, let action {
                Button(action: action) {
                    HStack(spacing: 4) {
                        Text(actionTitle)
                            .font(.system(size: 13, weight: .semibold))
                        Image(systemName: "arrow.up.right")
                            .font(.system(size: 10, weight: .heavy))
                    }
                    .foregroundStyle(Color.brandDusk.opacity(0.7))
                }
            }
        }
    }
}
