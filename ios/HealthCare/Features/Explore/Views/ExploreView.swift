import GoogleMobileAds
import SwiftUI

// MARK: - Explore Navigation Destinations
//
// MainTabView가 푸시 라우팅에서 explorePath에 직접 append할 수 있도록
// value 기반 enum으로 노출. NavigationLink도 같은 enum으로 통일.

enum ExploreDestination: Hashable {
    case weeklyRetrospective
    case changeAnalysis
}

struct ExploreView: View {
    @EnvironmentObject private var container: AppContainer

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 24) {
                InsightSectionHeader()
                InsightMenuGrid()
            }
            .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
            .padding(.top, Spacing.sm) // design-lint:ignore — micro/hero spacing
            .padding(.bottom, Spacing.xxxl) // design-lint:ignore — micro/hero spacing
        }
        .safeAreaInset(edge: .bottom) {
            BannerAdView(adUnitID: AdsManager.shared.bannerAdUnitID)
                .frame(height: 50)
        }
        .background(Color.backgroundPage)
        .navigationTitle(Text("explore.title"))
        .navigationBarTitleDisplayMode(.large)
        .navigationDestination(for: ExploreDestination.self) { dest in
            switch dest {
            case .weeklyRetrospective:
                WeeklyRetrospectiveView().environmentObject(container)
            case .changeAnalysis:
                ChangeAnalysisView().environmentObject(container)
            }
        }
    }
}

// MARK: - Section Header

private struct InsightSectionHeader: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(String(localized: "explore.insights.title"))
                .font(.labelSmall)
                .foregroundStyle(Color.textSecondary)
                .textCase(.uppercase)
                .tracking(0.5)
            Text(String(localized: "explore.insights.subtitle"))
                .font(.bodyMedium)
                .foregroundStyle(Color.textSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Menu Grid

private struct InsightMenuGrid: View {
    var body: some View {
        VStack(spacing: 12) {
            NavigationLink(value: ExploreDestination.weeklyRetrospective) {
                InsightMenuCard(
                    icon: "chart.bar.doc.horizontal",
                    iconColor: Color.brandAccent,
                    title: String(localized: "explore.card.retro.title"),
                    description: String(localized: "explore.card.retro.desc")
                )
            }
            .buttonStyle(.plain)

            NavigationLink(value: ExploreDestination.changeAnalysis) {
                InsightMenuCard(
                    icon: "waveform.path.ecg",
                    iconColor: .purple,
                    title: String(localized: "explore.card.change.title"),
                    description: String(localized: "explore.card.change.desc")
                )
            }
            .buttonStyle(.plain)
        }
    }
}

// MARK: - Card

private struct InsightMenuCard: View {
    let icon: String
    let iconColor: Color
    let title: String
    let description: String

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: icon)
                .font(.numeralMedium).fontWeight(.semibold)
                .foregroundStyle(iconColor)
                .frame(width: 50, height: 50)
                .background(iconColor.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: Radius.lg))

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.headingSmall).fontWeight(.bold)
                    .foregroundStyle(Color.textPrimary)
                Text(description)
                    .font(.bodySmall)
                    .foregroundStyle(Color.textSecondary)
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.captionBold)
                .foregroundStyle(Color.textSecondary.opacity(0.5))
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 2)
    }
}
