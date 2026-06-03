import SwiftUI

// MARK: - StyledTextField
//
// 앱 표준 텍스트 입력 필드. 좌측 아이콘 + placeholder + 단일 라인 입력.
//
// 사용:
// ```
// StyledTextField(icon: "envelope", placeholder: "이메일", text: $email)
//     .textInputAutocapitalization(.never)
//     .keyboardType(.emailAddress)
// ```

struct StyledTextField: View {
    let icon: String
    let placeholder: String
    @Binding var text: String

    var body: some View {
        HStack(spacing: Spacing.md) {
            Image(systemName: icon)
                .foregroundStyle(Color.brandPrimary)
                .frame(width: 20)

            TextField(placeholder, text: $text)
                .font(.bodyMedium)
        }
        .styledFieldBackground()
    }
}

// MARK: - StyledSecureField
//
// 비밀번호 입력 필드. 좌측 아이콘 + placeholder + 가시성 토글.

struct StyledSecureField: View {
    let icon: String
    let placeholder: String
    @Binding var text: String
    @State private var isVisible = false

    var body: some View {
        HStack(spacing: Spacing.md) {
            Image(systemName: icon)
                .foregroundStyle(Color.brandPrimary)
                .frame(width: 20)

            if isVisible {
                TextField(placeholder, text: $text)
                    .font(.bodyMedium)
            } else {
                SecureField(placeholder, text: $text)
                    .font(.bodyMedium)
            }

            Button {
                isVisible.toggle()
            } label: {
                Image(systemName: isVisible ? "eye.slash" : "eye")
                    .foregroundStyle(Color.textSecondary)
                    .accessibilityLabel(isVisible
                        ? String(localized: "auth.password.toggleVisibility.hide")
                        : String(localized: "auth.password.toggleVisibility.show"))
            }
        }
        .styledFieldBackground()
    }
}

// MARK: - Shared field chrome
//
// 두 필드가 공유하는 카드 배경 + 보더 + 섀도. 한 곳만 손보면 둘 다 따라온다.

private extension View {
    func styledFieldBackground() -> some View {
        self
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.lg)
            .background(Color.surfaceCard)
            .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.md, style: .continuous)
                    .stroke(Color.brandPrimary.opacity(0.2), lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 2)
    }
}
