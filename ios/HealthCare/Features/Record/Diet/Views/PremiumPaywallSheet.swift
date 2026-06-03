import SwiftUI

/// 프리미엄 전용 기능 진입 시 표시되는 안내 시트.
/// 1차 버전은 안내·소개만. StoreKit 결제 플로우는 후속 PR에서 연결한다.
struct PremiumPaywallSheet: View {
    @Binding var isPresented: Bool

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    headerSection
                    featureList
                    upcomingNotice
                    Spacer(minLength: 32)
                }
                .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
                .padding(.top, Spacing.xxl) // design-lint:ignore — micro/hero spacing
            }
            .background(Color.backgroundPage)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(String(localized: "common.close")) { isPresented = false }
                        .foregroundColor(Color.brandAccent)
                }
            }
        }
    }

    private var headerSection: some View {
        VStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(Color.brandPrimary.opacity(0.12))
                    .frame(width: 88, height: 88)
                Image(systemName: "sparkles")
                    .font(.system(size: 38, weight: .semibold)) // design-lint:ignore — SF Symbol/hero
                    .foregroundColor(Color.brandPrimary)
            }
            Text("Gainsy PRO")
                .font(.title2.bold())
            Text("AI 사진 영양 분석은 PRO 전용 기능이에요")
                .font(.subheadline)
                .foregroundColor(Color.textSecondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }

    private var featureList: some View {
        VStack(alignment: .leading, spacing: 14) {
            featureRow(
                icon: "camera.viewfinder",
                title: String(localized: "diet.paywall.feature1.title"),
                detail: String(localized: "diet.paywall.feature1.detail")
            )
            featureRow(
                icon: "list.bullet.rectangle.portrait",
                title: String(localized: "diet.paywall.feature2.title"),
                detail: String(localized: "diet.paywall.feature2.detail")
            )
            featureRow(
                icon: "checkmark.seal",
                title: String(localized: "diet.paywall.feature3.title"),
                detail: String(localized: "diet.paywall.feature3.detail")
            )
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
    }

    private func featureRow(icon: String, title: String, detail: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundColor(Color.brandPrimary)
                .frame(width: 28)
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.subheadline.bold())
                Text(detail)
                    .font(.caption)
                    .foregroundColor(Color.textSecondary)
            }
        }
    }

    private var upcomingNotice: some View {
        VStack(spacing: 10) {
            Image(systemName: "hourglass")
                .font(.headline)
                .foregroundColor(.orange)
            Text("구독 결제는 준비 중이에요")
                .font(.subheadline.bold())
            Text("PRO 구독은 곧 앱스토어 결제로 만나보실 수 있어요.\n출시 알림이 필요하시면 마이페이지에서 신청해 주세요.")
                .font(.caption)
                .foregroundColor(Color.textSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .frame(maxWidth: .infinity)
        .background(Color.surfaceCard.opacity(0.6))
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
    }
}
