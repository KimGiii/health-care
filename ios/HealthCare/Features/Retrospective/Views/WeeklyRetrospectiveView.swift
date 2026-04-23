import SwiftUI

struct WeeklyRetrospectiveView: View {
    @StateObject private var viewModel = WeeklyRetrospectiveViewModel()
    @EnvironmentObject private var container: AppContainer

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 20) {
                    WeekNavigationBar(
                        weekRange: viewModel.summary?.formattedWeekRange ?? "이번 주",
                        canGoNext: viewModel.weekOffset > 0,
                        onPrev: { viewModel.goToPreviousWeek(apiClient: container.apiClient) },
                        onNext: { viewModel.goToNextWeek(apiClient: container.apiClient) }
                    )

                    if viewModel.isLoading {
                        ProgressView().padding(.top, 60)
                    } else if let s = viewModel.summary {
                        WeeklySummaryContent(summary: s)
                    } else {
                        WeeklyEmptyState()
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 40)
            }
            .background(Color.surfaceGrouped)
            .navigationTitle("주간 회고")
            .navigationBarTitleDisplayMode(.large)
            .alert("오류", isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.errorMessage = nil } }
            )) {
                Button("확인", role: .cancel) {}
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
            .refreshable { await viewModel.load(apiClient: container.apiClient) }
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
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Color.brandPrimary)
                    .frame(width: 36, height: 36)
                    .background(Color.brandSurface)
                    .clipShape(Circle())
            }
            Spacer()
            Text(weekRange)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Color.textPrimary)
            Spacer()
            Button(action: onNext) {
                Image(systemName: "chevron.right")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(canGoNext ? Color.brandPrimary : Color.textSecondary)
                    .frame(width: 36, height: 36)
                    .background(Color.brandSurface)
                    .clipShape(Circle())
            }
            .disabled(!canGoNext)
        }
        .padding(.top, 8)
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
        InsightCard(title: "운동", icon: "figure.run", color: .cyan) {
            HStack(spacing: 0) {
                InsightStat(
                    label: "세션",
                    value: "\(summary.exerciseSessionCount)회",
                    color: .cyan
                )
                Divider().frame(height: 40)
                InsightStat(
                    label: "총 시간",
                    value: "\(summary.totalExerciseMinutes)분",
                    color: .cyan
                )
                if let cal = summary.totalCaloriesBurned {
                    Divider().frame(height: 40)
                    InsightStat(
                        label: "소모 칼로리",
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
        InsightCard(title: "식단", icon: "fork.knife", color: .orange) {
            HStack(spacing: 0) {
                InsightStat(
                    label: "기록",
                    value: "\(summary.dietLogCount)회",
                    color: .orange
                )
                if let cal = summary.avgDailyCalories {
                    Divider().frame(height: 40)
                    InsightStat(
                        label: "일평균 칼로리",
                        value: String(format: "%.0f kcal", cal),
                        color: .orange
                    )
                }
                if let pro = summary.avgDailyProteinG {
                    Divider().frame(height: 40)
                    InsightStat(
                        label: "일평균 단백질",
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
        InsightCard(title: "신체", icon: "scalemass.fill", color: Color.brandPrimary) {
            HStack(spacing: 0) {
                if let w = summary.latestWeightKg {
                    InsightStat(
                        label: "현재 체중",
                        value: String(format: "%.1f kg", w),
                        color: Color.brandPrimary
                    )
                }
                if let bf = summary.latestBodyFatPct {
                    Divider().frame(height: 40)
                    InsightStat(
                        label: "체지방",
                        value: String(format: "%.1f%%", bf),
                        color: Color.brandPrimary
                    )
                }
                if let change = summary.weightChangeKg {
                    Divider().frame(height: 40)
                    let sign = change >= 0 ? "+" : ""
                    InsightStat(
                        label: "주간 변화",
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
        InsightCard(title: "목표", icon: "target", color: .purple) {
            HStack(spacing: 0) {
                if let pct = summary.activeGoalPercentComplete {
                    InsightStat(
                        label: "달성률",
                        value: String(format: "%.0f%%", pct),
                        color: .purple
                    )
                }
                if let status = summary.activeGoalTrackingStatus {
                    Divider().frame(height: 40)
                    InsightStat(
                        label: "상태",
                        value: trackingLabel(status),
                        color: trackingColor(status)
                    )
                }
            }
        }
    }

    private func trackingLabel(_ status: String) -> String {
        switch status {
        case "AHEAD":           return "초과 달성"
        case "ON_TRACK":        return "순조로움"
        case "SLIGHTLY_BEHIND": return "조금 뒤처짐"
        case "BEHIND":          return "페이스업 필요"
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
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(color)
                Text(title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.textPrimary)
            }
            content()
        }
        .padding(18)
        .frame(maxWidth: .infinity)
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: 16))
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
                .font(.system(size: 18, weight: .bold, design: .rounded))
                .foregroundStyle(color)
            Text(label)
                .font(.system(size: 12))
                .foregroundStyle(Color.textSecondary)
        }
        .frame(maxWidth: .infinity)
    }
}

private struct WeeklyEmptyState: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "chart.bar.doc.horizontal")
                .font(.system(size: 48))
                .foregroundStyle(Color.textSecondary.opacity(0.5))
            Text("이 주에는 기록이 없어요")
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(Color.textSecondary)
        }
        .padding(.top, 60)
    }
}
