import SwiftUI

/// Apple/Google 신규 가입자에게 회원가입 직전 약관·개인정보처리방침 동의를 받는 sheet.
/// SignUpView 의 consentRow 스타일과 동일.
struct SocialConsentSheet: View {
    let provider: SocialAuthProvider
    let isLoading: Bool
    let onAgree: () -> Void
    let onCancel: () -> Void

    @State private var isTermsAgreed = false
    @State private var isPrivacyAgreed = false

    private let termsURL   = URL(string: "https://gainsy.site/terms")!
    private let privacyURL = URL(string: "https://gainsy.site/privacy")!

    private var canSubmit: Bool { isTermsAgreed && isPrivacyAgreed }

    private var providerName: String {
        switch provider {
        case .apple:  return "Apple"
        case .google: return "Google"
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Spacer().frame(height: 32)

                VStack(alignment: .leading, spacing: 12) {
                    Text("\(providerName) 계정으로 가입하기")
                        .font(.headingLarge)
                        .foregroundStyle(Color.textPrimary)
                    Text("서비스 이용을 위해 약관에 동의해 주세요.")
                        .font(.bodyMedium)
                        .foregroundStyle(Color.textSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing

                Spacer()

                VStack(spacing: 16) {
                    VStack(spacing: 10) {
                        consentRow(
                            isChecked: $isTermsAgreed,
                            label: String(localized: "auth.consent.terms"),
                            url: termsURL
                        )
                        consentRow(
                            isChecked: $isPrivacyAgreed,
                            label: String(localized: "auth.consent.privacy"),
                            url: privacyURL
                        )
                    }
                    .padding(.horizontal, Spacing.xs) // design-lint:ignore — micro/hero spacing

                    PrimaryButton(
                        String(localized: "auth.consent.continue"),
                        isEnabled: canSubmit,
                        isLoading: isLoading
                    ) {
                        onAgree()
                    }
                }
                .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing
                .padding(.bottom, 48) // design-lint:ignore — micro/hero spacing
            }
            .background(Color.backgroundPage.ignoresSafeArea())
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(String(localized: "common.cancel.button")) { onCancel() }
                        .foregroundColor(Color.brandAccent)
                }
            }
        }
        .interactiveDismissDisabled(isLoading)
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
                    .foregroundStyle(Color.textSecondary)
            }
            Spacer()
        }
    }
}
