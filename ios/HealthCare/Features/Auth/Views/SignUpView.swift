import SwiftUI

struct SignUpView: View {
    @StateObject private var viewModel = SignUpViewModel()
    @EnvironmentObject private var authState: AuthState
    @EnvironmentObject private var container: AppContainer

    var body: some View {
        ZStack {
            Color(hex: "#F5F4EC").ignoresSafeArea()

            VStack(spacing: 0) {
                // Header
                VStack(spacing: 8) {
                    BrandLogoView(size: 72, color: Color.brandPrimary)
                        .padding(.bottom, 4)

                    Text("함께 시작해봐요")
                        .font(.system(size: 22, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.brandPrimary)

                    Text("건강한 습관의 첫 걸음")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.textSecondary)
                }
                .padding(.top, 36)
                .padding(.bottom, 32)

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
                .padding(.horizontal, 28)

                // Error
                if let error = viewModel.errorMessage {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(Color.brandDanger)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 28)
                        .padding(.top, 8)
                }

                Spacer()

                // CTA
                VStack(spacing: 0) {
                    Button {
                        Task { await viewModel.register(apiClient: container.apiClient, authState: authState) }
                    } label: {
                        Group {
                            if viewModel.isLoading {
                                ProgressView().tint(.white)
                            } else {
                                Text("가입하기")
                                    .font(.system(size: 17, weight: .semibold))
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(Color.brandPrimary)
                        .foregroundStyle(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .shadow(color: Color.brandPrimary.opacity(0.3), radius: 10, x: 0, y: 5)
                    }
                    .disabled(viewModel.isLoading)
                    .opacity(viewModel.isLoading ? 0.7 : 1)

                    // Terms note
                    Text("가입 시 이용약관 및 개인정보처리방침에 동의하는 것으로 간주됩니다.")
                        .font(.system(size: 11))
                        .foregroundStyle(Color.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.top, 12)
                }
                .padding(.horizontal, 28)
                .padding(.bottom, 48)
            }
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
    }
}
