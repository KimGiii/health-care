import SwiftUI

struct LoginView: View {
    @StateObject private var viewModel = LoginViewModel()
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

                    Text("다시 만나서 반가워요")
                        .font(.headingLarge)
                        .foregroundStyle(Color.brandPrimary)

                    Text("계속하려면 로그인해 주세요")
                        .font(.bodyMedium)
                        .foregroundStyle(Color.textSecondary)
                }
                .padding(.top, Spacing.xxxl) // design-lint:ignore — micro/hero spacing
                .padding(.bottom, 36) // design-lint:ignore — micro/hero spacing

                // Form
                VStack(spacing: 14) {
                    StyledTextField(
                        icon:        "envelope",
                        placeholder: "이메일",
                        text:        $viewModel.email
                    )
                    .textInputAutocapitalization(.never)
                    .keyboardType(.emailAddress)

                    StyledSecureField(
                        icon:        "lock",
                        placeholder: "비밀번호",
                        text:        $viewModel.password
                    )
                }
                .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing

                // Error
                if let error = viewModel.errorMessage {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(Color.brandDanger)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing
                        .padding(.top, Spacing.sm) // design-lint:ignore — micro/hero spacing
                }

                Spacer()

                // CTA
                VStack(spacing: Spacing.md) {
                    PrimaryButton(
                        "로그인하기",
                        isEnabled: !viewModel.email.isEmpty && !viewModel.password.isEmpty,
                        isLoading: viewModel.isLoading
                    ) {
                        Task { await viewModel.login(apiClient: container.apiClient, authState: authState) }
                    }

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

                    GoogleSignInButton(isLoading: viewModel.isLoading) {
                        Task {
                            await viewModel.signInWithSocialProvider(
                                .google,
                                tokenProvider: GoogleSignInCoordinator(),
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

// StyledTextField/StyledSecureField는 DesignSystem/Components/StyledTextField.swift로 승격됨.
