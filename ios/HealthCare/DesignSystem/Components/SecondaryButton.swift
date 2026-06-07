import SwiftUI

// MARK: - SecondaryButtonLabel

struct SecondaryButtonLabel: View {
    let title: String

    var body: some View {
        Text(title)
            .font(.cta)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.lg)
            .background(Color.brandPrimary.opacity(0.08))
            .foregroundStyle(Color.brandPrimary)
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.lg, style: .continuous)
                    .stroke(Color.brandPrimary.opacity(0.25), lineWidth: 1)
            )
    }
}

// MARK: - SecondaryButton
//
// 앱 표준 보조(secondary) CTA. Outline 스타일.
// 회원가입 진입, 건너뛰기, 나중에 하기 같은 약한 액션에 사용한다.

struct SecondaryButton: View {
    let title: String
    let isEnabled: Bool
    let action: () -> Void

    init(
        _ title: String,
        isEnabled: Bool = true,
        action: @escaping () -> Void
    ) {
        self.title     = title
        self.isEnabled = isEnabled
        self.action    = action
    }

    var body: some View {
        Button(action: action) {
            SecondaryButtonLabel(title: title)
        }
        .disabled(!isEnabled)
        .opacity(isEnabled ? 1 : 0.55)
    }
}
