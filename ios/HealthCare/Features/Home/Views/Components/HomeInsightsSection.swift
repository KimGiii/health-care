import SwiftUI

// MARK: - Home Insights Section
//
// 구 Explore 탭을 홈으로 흡수(#93). 주간회고·변화분석 진입점을 홈 하단에 배치해
// 리텐션 위클리 층을 홈 1뎁스로 끌어올린다. 네비게이션은 HomeDestination을 사용한다.

struct HomeInsightsSection: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            header
            NavigationLink(value: HomeDestination.weeklyRetrospective) {
                InsightMenuCard(
                    icon: "chart.bar.doc.horizontal",
                    iconColor: Color.brandAccent,
                    title: String(localized: "explore.card.retro.title"),
                    description: String(localized: "explore.card.retro.desc")
                )
            }
            .buttonStyle(.plain)

            NavigationLink(value: HomeDestination.changeAnalysis) {
                InsightMenuCard(
                    icon: "waveform.path.ecg",
                    iconColor: .purple,
                    title: String(localized: "explore.card.change.title"),
                    description: String(localized: "explore.card.change.desc")
                )
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
    }

    private var header: some View {
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
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }
}
