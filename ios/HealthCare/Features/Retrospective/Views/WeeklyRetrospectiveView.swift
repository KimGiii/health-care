import SwiftUI

struct WeeklyRetrospectiveView: View {
    @StateObject private var viewModel = WeeklyRetrospectiveViewModel()
    @EnvironmentObject private var container: AppContainer

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 20) {
                WeekNavigationBar(
                    weekRange: viewModel.summary?.formattedWeekRange ?? String(localized: "retro.weekRange.thisWeek"),
                    canGoNext: viewModel.weekOffset > 0,
                    onPrev: { viewModel.goToPreviousWeek(apiClient: container.apiClient) },
                    onNext: { viewModel.goToNextWeek(apiClient: container.apiClient) }
                )

                if viewModel.isLoading {
                    ProgressView().padding(.top, 60) // design-lint:ignore — micro/hero spacing
                } else if let s = viewModel.summary {
                    WeeklySummaryContent(summary: s)
                } else {
                    WeeklyEmptyState()
                }
            }
            .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
            .padding(.bottom, Spacing.xxxl) // design-lint:ignore — micro/hero spacing
        }
        .background(Color.backgroundPage)
        .navigationTitle(Text("retro.title"))
        .navigationBarTitleDisplayMode(.large)
        .alert(Text("오류"), isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button(String(localized: "common.ok"), role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
        .refreshable {
            await viewModel.load(apiClient: container.apiClient)
            viewModel.errorMessage = nil
        }
        .task { await viewModel.load(apiClient: container.apiClient) }
    }
}

// MARK: - Week Navigation

private struct WeekNavigationBar: View {
    let weekRange: String
    let canGoNext: Bool
    let onPrev: () -> Void
    let onNext: () -> Void

    var body: some View {
        HStack {
            Button(action: onPrev) {
                Image(systemName: "chevron.left")
                    .font(.bodyLarge).fontWeight(.semibold)
                    .foregroundStyle(Color.textHeadline)
                    .frame(width: 36, height: 36)
                    .background(Color.surfaceCard)
                    .clipShape(Circle())
            }
            Spacer()
            Text(weekRange)
                .font(.headingSmall)
                .foregroundStyle(Color.textPrimary)
            Spacer()
            Button(action: onNext) {
                Image(systemName: "chevron.right")
                    .font(.bodyLarge).fontWeight(.semibold)
                    // 활성=textHeadline(적응형 진함), 비활성=textTertiary(적응형 흐림)
                    // brandPrimary는 다크 배경에서 안 보여 swap된 것처럼 보이던 문제 해결.
                    .foregroundStyle(canGoNext ? Color.textHeadline : Color.textTertiary)
                    .frame(width: 36, height: 36)
                    .background(Color.surfaceCard)
                    .clipShape(Circle())
                    .opacity(canGoNext ? 1.0 : 0.6)
            }
            .disabled(!canGoNext)
        }
        .padding(.top, Spacing.sm) // design-lint:ignore — micro/hero spacing
    }
}

// MARK: - Summary Content

private struct WeeklySummaryContent: View {
    let summary: WeeklySummaryResponse

    var body: some View {
        VStack(spacing: 16) {
            ExerciseSummaryCard(summary: summary)
            DietSummaryCard(summary: summary)
            BodySummaryCard(summary: summary)
            if summary.activeGoalPercentComplete != nil || summary.activeGoalTrackingStatus != nil {
                GoalSummaryCard(summary: summary)
            }
        }
    }
}

// MARK: - Exercise Card

private struct ExerciseSummaryCard: View {
    let summary: WeeklySummaryResponse

    var body: some View {
        InsightCard(title: String(localized: "retro.card.exercise"), icon: "figure.run", color: .cyan) {
            HStack(spacing: 0) {
                InsightStat(
                    label: String(localized: "retro.stat.sessions"),
                    value: String(format: String(localized: "retro.stat.sessions.value"), summary.exerciseSessionCount),
                    color: .cyan
                )
                Divider().frame(height: 40)
                InsightStat(
                    label: String(localized: "retro.stat.totalTime"),
                    value: String(format: String(localized: "retro.stat.totalTime.value"), summary.totalExerciseMinutes),
                    color: .cyan
                )
                if let cal = summary.totalCaloriesBurned {
                    Divider().frame(height: 40)
                    InsightStat(
                        label: String(localized: "retro.stat.burned"),
                        value: String(format: "%.0f kcal", cal),
                        color: .cyan
                    )
                }
            }
        }
    }
}

// MARK: - Diet Card

private struct DietSummaryCard: View {
    let summary: WeeklySummaryResponse

    var body: some View {
        InsightCard(title: String(localized: "retro.card.diet"), icon: "fork.knife", color: .orange) {
            HStack(spacing: 0) {
                InsightStat(
                    label: String(localized: "retro.stat.logs"),
                    value: String(format: String(localized: "retro.stat.logs.value"), summary.dietLogCount),
                    color: .orange
                )
                if let cal = summary.avgDailyCalories {
                    Divider().frame(height: 40)
                    InsightStat(
                        label: String(localized: "retro.stat.avgKcal"),
                        value: String(format: "%.0f kcal", cal),
                        color: .orange
                    )
                }
                if let pro = summary.avgDailyProteinG {
                    Divider().frame(height: 40)
                    InsightStat(
                        label: String(localized: "retro.stat.avgProtein"),
                        value: String(format: "%.0f g", pro),
                        color: .orange
                    )
                }
            }
        }
    }
}

// MARK: - Body Card

private struct BodySummaryCard: View {
    let summary: WeeklySummaryResponse

    var body: some View {
        InsightCard(title: String(localized: "retro.card.body"), icon: "scalemass.fill", color: Color.brandPrimary) {
            HStack(spacing: 0) {
                if let w = summary.latestWeightKg {
                    InsightStat(
                        label: String(localized: "retro.stat.currentWeight"),
                        value: String(format: "%.1f kg", w),
                        color: Color.brandPrimary
                    )
                }
                if let bf = summary.latestBodyFatPct {
                    Divider().frame(height: 40)
                    InsightStat(
                        label: String(localized: "retro.stat.bodyFat"),
                        value: String(format: "%.1f%%", bf),
                        color: Color.brandPrimary
                    )
                }
                if let change = summary.weightChangeKg {
                    Divider().frame(height: 40)
                    let sign = change >= 0 ? "+" : ""
                    InsightStat(
                        label: String(localized: "retro.stat.weeklyChange"),
                        value: String(format: "%@%.1f kg", sign, change),
                        color: change < 0 ? .green : (change > 0 ? Color.brandDanger : Color.textSecondary)
                    )
                }
            }
        }
    }
}

// MARK: - Goal Card

private struct GoalSummaryCard: View {
    let summary: WeeklySummaryResponse

    var body: some View {
        InsightCard(title: String(localized: "retro.card.goal"), icon: "target", color: .purple) {
            HStack(spacing: 0) {
                if let pct = summary.activeGoalPercentComplete {
                    InsightStat(
                        label: String(localized: "retro.stat.progress"),
                        value: String(format: "%.0f%%", pct),
                        color: .purple
                    )
                }
                if let status = summary.activeGoalTrackingStatus {
                    Divider().frame(height: 40)
                    InsightStat(
                        label: String(localized: "retro.stat.status"),
                        value: trackingLabel(status),
                        color: trackingColor(status)
                    )
                }
            }
        }
    }

    private func trackingLabel(_ status: String) -> String {
        switch status {
        case "AHEAD":           return String(localized: "retro.status.ahead")
        case "ON_TRACK":        return String(localized: "retro.status.onTrack")
        case "SLIGHTLY_BEHIND": return String(localized: "retro.status.slightlyBehind")
        case "BEHIND":          return String(localized: "retro.status.behind")
        default:                return status
        }
    }

    private func trackingColor(_ status: String) -> Color {
        switch status {
        case "AHEAD", "ON_TRACK": return .green
        case "SLIGHTLY_BEHIND":   return .orange
        case "BEHIND":            return Color.brandDanger
        default:                  return Color.textSecondary
        }
    }
}

// MARK: - Shared Components

private struct InsightCard<Content: View>: View {
    let title: String
    let icon: String
    let color: Color
    let content: () -> Content

    init(title: String, icon: String, color: Color, @ViewBuilder content: @escaping () -> Content) {
        self.title = title
        self.icon = icon
        self.color = color
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.bodyMedium).fontWeight(.semibold)
                    .foregroundStyle(color)
                Text(title)
                    .font(.headingSmall)
                    .foregroundStyle(Color.textPrimary)
            }
            content()
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .frame(maxWidth: .infinity)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 2)
    }
}

private struct InsightStat: View {
    let label: String
    let value: String
    let color: Color

    var body: some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.numeralMedium).fontWeight(.bold)
                .foregroundStyle(color)
            Text(label)
                .font(.caption)
                .foregroundStyle(Color.textSecondary)
        }
        .frame(maxWidth: .infinity)
    }
}

private struct WeeklyEmptyState: View {
    var body: some View {
        EmptyState(
            icon: "chart.bar.doc.horizontal",
            title: String(localized: "retro.empty.title")
        )
    }
}
