import SwiftUI

struct LoginView: View {
    @StateObject private var viewModel = LoginViewModel()
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

                    Text("다시 만나서 반가워요")
                        .font(.system(size: 22, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.brandPrimary)

                    Text("계속하려면 로그인해주세요")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.textSecondary)
                }
                .padding(.top, 40)
                .padding(.bottom, 36)

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
                VStack(spacing: 12) {
                    Button {
                        Task { await viewModel.login(apiClient: container.apiClient, authState: authState) }
                    } label: {
                        Group {
                            if viewModel.isLoading {
                                ProgressView().tint(.white)
                            } else {
                                Text("로그인")
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
                    .disabled(viewModel.isLoading || viewModel.email.isEmpty || viewModel.password.isEmpty)
                    .opacity(viewModel.email.isEmpty || viewModel.password.isEmpty ? 0.55 : 1)
                }
                .padding(.horizontal, 28)
                .padding(.bottom, 48)
            }
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Styled Input Components
struct StyledTextField: View {
    let icon: String
    let placeholder: String
    @Binding var text: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundStyle(Color.brandPrimary)
                .frame(width: 20)

            TextField(placeholder, text: $text)
                .font(.system(size: 15))
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.brandPrimary.opacity(0.2), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 2)
    }
}

struct StyledSecureField: View {
    let icon: String
    let placeholder: String
    @Binding var text: String
    @State private var isVisible = false

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundStyle(Color.brandPrimary)
                .frame(width: 20)

            if isVisible {
                TextField(placeholder, text: $text)
                    .font(.system(size: 15))
            } else {
                SecureField(placeholder, text: $text)
                    .font(.system(size: 15))
            }

            Button {
                isVisible.toggle()
            } label: {
                Image(systemName: isVisible ? "eye.slash" : "eye")
                    .foregroundStyle(Color.textSecondary)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.brandPrimary.opacity(0.2), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 2)
    }
}
