import SwiftUI

struct RootView: View {
    @EnvironmentObject private var authState: AuthState
    @State private var showSplash = true

    var body: some View {
        ZStack {
            // ── Main Content ──────────────────────────────────────────
            Group {
                switch authState.status {
                case .unauthenticated:
                    OnboardingView()
                case .profileSetup:
                    ProfileSetupView()
                case .authenticated:
                    MainTabView()
                case .loading:
                    Color(hex: "#F5F4EC").ignoresSafeArea()
                }
            }

            // ── Splash Overlay ────────────────────────────────────────
            if showSplash {
                SplashView()
                    .transition(
                        .asymmetric(
                            insertion: .opacity,
                            removal:   .opacity.combined(with: .scale(scale: 1.05))
                        )
                    )
                    .zIndex(1)
            }
        }
        .animation(.easeInOut(duration: 0.45), value: showSplash)
        .onAppear {
            Task {
                try? await Task.sleep(for: .seconds(2.0))
                showSplash = false
            }
        }
    }
}
