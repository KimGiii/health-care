import SwiftUI

/// 이메일·비밀번호 회원가입 폼. `SignUpView` 진입에서 "Gainsy 가입하기" 탭 시 push.
struct EmailSignUpView: View {
    @StateObject private var viewModel = SignUpViewModel()
    @EnvironmentObject private var authState: AuthState
    @EnvironmentObject private var container: AppContainer

    @State private var isTermsAgreed = false
    @State private var isPrivacyAgreed = false

    private let termsURL = URL(string: "https://gainsy.site/terms")!
    private let privacyURL = URL(string: "https://gainsy.site/privacy")!

    private var canSubmit: Bool {
        !viewModel.isLoading && isTermsAgreed && isPrivacyAgreed
    }

    var body: some View {
        ZStack {
            Color.backgroundPage.ignoresSafeArea()

            VStack(spacing: 0) {
                // Header
                VStack(spacing: 8) {
                    Text("Gainsy 계정으로 가입")
                        .font(.headingLarge)
                        .foregroundStyle(Color.brandPrimary)

                    Text("이메일과 비밀번호로 시작하세요")
                        .font(.bodyMedium)
                        .foregroundStyle(Color.textSecondary)
                }
                .padding(.top, 24) // design-lint:ignore — micro/hero spacing
                .padding(.bottom, Spacing.xxl) // design-lint:ignore — micro/hero spacing

                // Form
                VStack(spacing: 14) {
                    StyledTextField(
                        icon:        "person",
                        placeholder: "닉네임",
                        text:        $viewModel.displayName
                    )

                    StyledTextField(
                        icon:        "envelope",
                        placeholder: "이메일",
                        text:        $viewModel.email
                    )
                    .textInputAutocapitalization(.never)
                    .keyboardType(.emailAddress)

                    StyledSecureField(
                        icon:        "lock",
                        placeholder: "비밀번호 (8자 이상)",
                        text:        $viewModel.password
                    )

                    StyledSecureField(
                        icon:        "lock.shield",
                        placeholder: "비밀번호 확인",
                        text:        $viewModel.passwordConfirm
                    )
                }
                .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing

                if let error = viewModel.errorMessage {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(Color.brandDanger)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing
                        .padding(.top, Spacing.sm) // design-lint:ignore — micro/hero spacing
                }

                Spacer()

                VStack(spacing: 16) {
                    VStack(spacing: 10) {
                        consentRow(isChecked: $isTermsAgreed, label: "이용약관", url: termsURL)
                        consentRow(isChecked: $isPrivacyAgreed, label: "개인정보처리방침", url: privacyURL)
                    }
                    .padding(.horizontal, Spacing.xs) // design-lint:ignore — micro/hero spacing

                    PrimaryButton(
                        "가입하기",
                        isEnabled: canSubmit,
                        isLoading: viewModel.isLoading
                    ) {
                        Task { await viewModel.register(apiClient: container.apiClient, authState: authState) }
                    }
                }
                .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing
                .padding(.bottom, 48) // design-lint:ignore — micro/hero spacing
            }
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func consentRow(isChecked: Binding<Bool>, label: String, url: URL) -> some View {
        HStack(spacing: 10) {
            Button {
                isChecked.wrappedValue.toggle()
            } label: {
                Image(systemName: isChecked.wrappedValue ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 22)) // design-lint:ignore — SF Symbol size
                    .foregroundStyle(isChecked.wrappedValue ? Color.brandPrimary : Color.textSecondary.opacity(0.5))
                    .animation(.easeInOut(duration: 0.15), value: isChecked.wrappedValue)
            }

            HStack(spacing: 4) {
                Text("(필수)")
                    .font(.captionBold)
                    .foregroundStyle(Color.brandDanger)

                Link(label, destination: url)
                    .font(.bodySmall)
                    .fontWeight(.medium)
                    .foregroundStyle(Color.brandPrimary)
                    .underline()

                Text("에 동의합니다")
                    .font(.bodySmall)
                    .foregroundStyle(Color.textPrimary)
            }

            Spacer()
        }
    }
}
