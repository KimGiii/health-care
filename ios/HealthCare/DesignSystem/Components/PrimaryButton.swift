import SwiftUI

/// Primary call-to-action for VITALITY.
/// Deep forest fill with subtle mint edge highlight and elevation.
struct PrimaryButton: View {
    let title: String
    let isLoading: Bool
    let action: () -> Void

    init(_ title: String, isLoading: Bool = false, action: @escaping () -> Void) {
        self.title     = title
        self.isLoading = isLoading
        self.action    = action
    }

    var body: some View {
        Button(action: action) {
            Group {
                if isLoading {
                    ProgressView()
                        .tint(.white)
                } else {
                    HStack(spacing: 8) {
                        Text(title)
                            .font(.system(size: 16, weight: .bold))
                            .tracking(0.3)
                        Image(systemName: "arrow.up.right")
                            .font(.system(size: 12, weight: .heavy))
                            .opacity(0.8)
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(
                ZStack {
                    LinearGradient.forestHero
                    // mint top sheen
                    LinearGradient(
                        colors: [Color.brandAccent.opacity(0.28), .clear],
                        startPoint: .top, endPoint: .center
                    )
                }
            )
            .foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Color.white.opacity(0.08), lineWidth: 0.8)
            )
            .elevation(.forest)
        }
        .disabled(isLoading)
    }
}
