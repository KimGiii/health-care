import SwiftUI

struct OnboardingView: View {
    @EnvironmentObject private var authState: AuthState
    @State private var showLogin = false

    var body: some View {
        NavigationStack {
            ZStack {
                // ── Background ──────────────────────────────────────
                Color(hex: "#F5F4EC").ignoresSafeArea()

                // ── Decorative blobs ─────────────────────────────────
                Circle()
                    .fill(Color.brandSurface)
                    .frame(width: 320, height: 320)
                    .blur(radius: 2)
                    .offset(x: -80, y: -260)

                Circle()
                    .fill(Color.brandSurface.opacity(0.6))
                    .frame(width: 200, height: 200)
                    .offset(x: 130, y: 200)

                // ── Content ──────────────────────────────────────────
                VStack(spacing: 0) {
                    Spacer()

                    // Logo
                    BrandLogoView(size: 160, color: Color.brandPrimary)
                        .padding(.bottom, 36)

                    // Copy
                    VStack(spacing: 10) {
                        Text("HealthCare")
                            .font(.system(size: 32, weight: .bold, design: .rounded))
                            .foregroundStyle(Color.brandPrimary)

                        Text("운동·식단·신체변화를 하나로\n매일 기록하고 꾸준히 성장하세요")
                            .font(.system(size: 15))
                            .foregroundStyle(Color.textSecondary)
                            .multilineTextAlignment(.center)
                            .lineSpacing(4)
                    }

                    Spacer()

                    // CTA Buttons
                    VStack(spacing: 12) {
                        NavigationLink(destination: SignUpView()) {
                            Text("시작하기")
                                .font(.system(size: 17, weight: .semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 16)
                                .background(Color.brandPrimary)
                                .foregroundStyle(.white)
                                .clipShape(RoundedRectangle(cornerRadius: 14))
                                .shadow(
                                    color: Color.brandPrimary.opacity(0.3),
                                    radius: 10, x: 0, y: 5
                                )
                        }

                        Button {
                            showLogin = true
                        } label: {
                            Text("이미 계정이 있어요")
                                .font(.system(size: 15, weight: .medium))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 16)
                                .background(Color.brandPrimary.opacity(0.08))
                                .foregroundStyle(Color.brandPrimary)
                                .clipShape(RoundedRectangle(cornerRadius: 14))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14)
                                        .stroke(Color.brandPrimary.opacity(0.25), lineWidth: 1)
                                )
                        }
                    }
                    .padding(.horizontal, 28)
                    .padding(.bottom, 48)
                }
            }
            .navigationDestination(isPresented: $showLogin) {
                LoginView()
            }
        }
    }
}
