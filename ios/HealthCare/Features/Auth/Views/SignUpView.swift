import SwiftUI

/// 회원가입 진입 화면. Gainsy(이메일) 가입과 소셜(Apple) 가입을 분기한다.
/// - Gainsy 가입하기 → `EmailSignUpView` push
/// - Apple로 가입하기 → 토큰 fetch → /check → 신규면 `SocialConsentSheet` 노출 → /commit
struct SignUpView: View {
    @StateObject private var viewModel = SignUpViewModel()
    @EnvironmentObject private var authState: AuthState
    @EnvironmentObject private var container: AppContainer

    var body: some View {
        ZStack {
            Color.backgroundPage.ignoresSafeArea()

            VStack(spacing: 0) {
                // Header
                VStack(spacing: 8) {
                    BrandLogoView(size: 72, color: Color.brandPrimary)
                        .padding(.bottom, Spacing.xs) // design-lint:ignore — micro/hero spacing

                    Text("함께 시작해 봐요")
                        .font(.headingLarge)
                        .foregroundStyle(Color.brandPrimary)

                    Text("건강한 습관의 첫 걸음")
                        .font(.bodyMedium)
                        .foregroundStyle(Color.textSecondary)
                }
                .padding(.top, 36) // design-lint:ignore — micro/hero spacing

                Spacer()

                if let error = viewModel.errorMessage {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(Color.brandDanger)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing
                        .padding(.bottom, Spacing.sm) // design-lint:ignore — micro/hero spacing
                }

                // CTA: 2개 분기
                VStack(spacing: 16) {
                    NavigationLink(destination: EmailSignUpView()) {
                        PrimaryButtonLabel(title: "Gainsy 계정으로 가입하기", isEnabled: !viewModel.isLoading)
                    }
                    .disabled(viewModel.isLoading)

                    OrDivider()

                    AppleSignInButton(isLoading: viewModel.isLoading) {
                        Task {
                            await viewModel.signInWithSocialProvider(
                                .apple,
                                tokenProvider: AppleSignInCoordinator(),
                                apiClient: container.apiClient,
                                authState: authState
                            )
                        }
                    }
                }
                .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing
                .padding(.bottom, 48) // design-lint:ignore — micro/hero spacing
            }
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $viewModel.pendingConsent) { pending in
            SocialConsentSheet(
                provider: pending.provider,
                isLoading: viewModel.isLoading,
                onAgree: {
                    Task { await viewModel.completeSocialSignUp(apiClient: container.apiClient, authState: authState) }
                },
                onCancel: {
                    viewModel.cancelSocialSignUp()
                }
            )
        }
    }
}
