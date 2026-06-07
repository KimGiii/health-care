import SwiftUI

/// Apple 가이드라인을 따르는 로그인 버튼 (검정 배경, Apple 로고 + "Apple로 계속하기").
struct AppleSignInButton: View {
    let isLoading: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.sm) {
                if isLoading {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(.white)
                } else {
                    Image(systemName: "applelogo")
                        .font(.system(size: 18, weight: .medium)) // design-lint:ignore Apple HIG 요구사항
                }
                Text("Apple로 계속하기")
                    .font(.cta)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.lg)
            .background(Color.black)
            .foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))
        }
        .disabled(isLoading)
    }
}

/// "또는" 좌우 분리선.
struct OrDivider: View {
    var body: some View {
        HStack(spacing: Spacing.md) {
            Rectangle()
                .fill(Color.textSecondary.opacity(0.2))
                .frame(height: 1)
            Text("또는")
                .font(.caption)
                .foregroundStyle(Color.textSecondary)
            Rectangle()
                .fill(Color.textSecondary.opacity(0.2))
                .frame(height: 1)
        }
    }
}
