import SwiftUI

// MARK: - Main View

struct GoalSettingView: View {
    @StateObject private var viewModel = GoalSettingViewModel()
    @EnvironmentObject private var container: AppContainer

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    GoalHeroSection(activeGoal: viewModel.activeGoal)

                    VStack(alignment: .leading, spacing: 28) {
                        if viewModel.isLoading {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                                .padding(.top, Spacing.xxxl) // design-lint:ignore — micro/hero spacing
                        } else if let active = viewModel.activeGoal {
                            ActiveGoalCard(
                                goal: active,
                                onAbandon: {
                                    Task {
                                        await viewModel.abandonGoal(
                                            id: active.id,
                                            apiClient: container.apiClient
                                        )
                                    }
                                },
                                progressDestination: {
                                    GoalProgressView(goalId: active.id)
                                        .environmentObject(container)
                                }
                            )
                            .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
                        } else {
                            EmptyGoalCard { viewModel.showAddGoal = true }
                                .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
                        }

                        if !viewModel.pastGoals.isEmpty {
                            PastGoalsSection(goals: viewModel.pastGoals)
                        }
                    }
                    .padding(.vertical, Spacing.xxl) // design-lint:ignore — micro/hero spacing
                    .padding(.bottom, 80) // design-lint:ignore — micro/hero spacing
                }
            }
            .ignoresSafeArea(edges: .top)
            .background(Color.backgroundPage)
            .refreshable {
                await viewModel.load(apiClient: container.apiClient)
                viewModel.errorMessage = nil
            }

            // + FAB
            Button { viewModel.showAddGoal = true } label: {
                Image(systemName: "plus")
                    .font(.system(size: 22, weight: .semibold)) // design-lint:ignore — SF Symbol or special
                    .foregroundStyle(.white)
                    .frame(width: 58, height: 58)
                    .background(Color.brandPrimary)
                    .clipShape(Circle())
                    .shadow(color: Color.brandPrimary.opacity(0.45), radius: 12, x: 0, y: 6)
            }
            .padding(.trailing, Spacing.xxl) // design-lint:ignore — micro/hero spacing
            .padding(.bottom, Spacing.xxl) // design-lint:ignore — micro/hero spacing
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $viewModel.showAddGoal) {
            AddGoalView { _ in
                Task { await viewModel.goalCreated(apiClient: container.apiClient) }
            }
        }
        .alert(Text("오류"), isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button(String(localized: "common.ok"), role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
        .task { await viewModel.load(apiClient: container.apiClient) }
    }
}

// MARK: - Hero Section

private struct GoalHeroSection: View {
    let activeGoal: GoalSummary?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack(alignment: .top) {
            GoalWaveBackground().frame(height: 300)

            VStack(spacing: 0) {
                HStack {
                    Button { dismiss() } label: {
                        Image(systemName: "chevron.left")
                            .font(.cta)
                            .foregroundStyle(.white)
                            .padding(Spacing.md) // design-lint:ignore — micro/hero spacing
                            .background(.white.opacity(0.15))
                            .clipShape(Circle())
                    }
                    Spacer()
                    Text(String(localized: "goal.settings.title"))
                        .font(.numeralMedium).fontWeight(.bold)
                        .foregroundStyle(.white)
                    Spacer()
                    Color.clear.frame(width: 40, height: 40)
                }
                .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
                .padding(.top, 56) // design-lint:ignore — micro/hero spacing

                if let goal = activeGoal {
                    GoalProgressRing(goal: goal).padding(.top, Spacing.lg)
                } else {
                    NoGoalPlaceholder().padding(.top, Spacing.lg)
                }
            }
        }
    }
}

private struct GoalWaveBackground: View {
    var body: some View {
        GeometryReader { geo in
            ZStack {
                Color.brandPrimary
                Ellipse()
                    .fill(Color.brandSecondary.opacity(0.5))
                    .frame(width: geo.size.width * 0.7, height: geo.size.height * 0.55)
                    .offset(x: geo.size.width * 0.3, y: -geo.size.height * 0.1)
                    .rotationEffect(.degrees(-15))
                GoalWaveCurve()
                    .fill(Color.backgroundPage)
                    .frame(height: 64)
                    .frame(maxWidth: .infinity)
                    .offset(y: geo.size.height - 32)
            }
        }
    }
}

private struct GoalWaveCurve: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 0, y: rect.height))
        p.addCurve(
            to: CGPoint(x: rect.width, y: rect.height),
            control1: CGPoint(x: rect.width * 0.3, y: 0),
            control2: CGPoint(x: rect.width * 0.7, y: rect.height * 0.5)
        )
        p.addLine(to: CGPoint(x: rect.width, y: rect.height))
        p.addLine(to: CGPoint(x: 0, y: rect.height))
        p.closeSubpath()
        return p
    }
}

private struct GoalProgressRing: View {
    let goal: GoalSummary

    var body: some View {
        HStack(spacing: 24) {
            ZStack {
                Circle()
                    .stroke(Color.white.opacity(0.2), lineWidth: 11)
                    .frame(width: 100, height: 100)
                Circle()
                    .trim(from: 0, to: goal.progressRatio)
                    .stroke(Color.brandAccent, style: StrokeStyle(lineWidth: 11, lineCap: .round))
                    .frame(width: 100, height: 100)
                    .rotationEffect(.degrees(-90))
                    .animation(.easeInOut(duration: 0.8), value: goal.progressRatio)
                VStack(spacing: 1) {
                    Text(String(format: "%.0f%%", goal.progressRatio * 100))
                        .font(.numeralMedium).fontWeight(.bold)
                        .foregroundStyle(.white)
                    Text(String(localized: "goal.summary.achieved"))
                        .font(.captionXSmall)
                        .foregroundStyle(.white.opacity(0.7))
                }
            }

            VStack(alignment: .leading, spacing: 9) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(goal.goalType.displayName)
                        .font(.cta).fontWeight(.bold)
                        .foregroundStyle(.white)
                    Text(String(format: String(localized: "goal.summary.target"), goal.targetText))
                        .font(.bodySmall)
                        .foregroundStyle(.white.opacity(0.8))
                }
                HStack(spacing: 5) {
                    Image(systemName: "calendar")
                        .font(.captionXSmall)
                    Text(goal.formattedTargetDate)
                        .font(.caption).fontWeight(.medium)
                }
                .foregroundStyle(.white.opacity(0.8))

                if let days = goal.daysRemaining {
                    DaysRemainingBadge(days: days)
                }
            }
        }
        .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing
    }
}

private struct DaysRemainingBadge: View {
    let days: Int
    private var color: Color { days >= 14 ? .brandAccent : (days >= 0 ? .brandWarning : .brandDanger) }

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "clock.fill").font(.captionXSmall)
            Text("D-\(days)").font(.captionXSmall).fontWeight(.semibold)
        }
        .foregroundStyle(color)
        .padding(.horizontal, Spacing.md) // design-lint:ignore — micro/hero spacing
        .padding(.vertical, Spacing.xs) // design-lint:ignore — micro/hero spacing
        .background(Color.white.opacity(0.15))
        .clipShape(Capsule())
    }
}

// 다크 hero 배경 안에 표시되는 빈 상태이므로 EmptyState(라이트 배경 전제) 대신
// 인라인 유지 + 카피만 통일 (docs/COPY.md §4).
private struct NoGoalPlaceholder: View {
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "target")
                .font(.system(size: 34)) // design-lint:ignore — SF Symbol size
                .foregroundStyle(.white.opacity(0.45))
            Text(String(localized: "goal.summary.empty"))
                .font(.bodyMedium)
                .foregroundStyle(.white.opacity(0.65))
        }
        .frame(height: 110)
    }
}

// MARK: - Active Goal Card

private struct ActiveGoalCard<Destination: View>: View {
    let goal: GoalSummary
    let onAbandon: () -> Void
    let progressDestination: () -> Destination
    @State private var showAbandonConfirm = false

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                HStack(spacing: 10) {
                    Image(systemName: goal.goalType.icon)
                        .font(.cta)
                        .foregroundStyle(Color.brandPrimary)
                        .frame(width: 40, height: 40)
                        .background(Color.surfaceCard)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(String(localized: "goal.detail.active"))
                            .font(.captionXSmall)
                            .foregroundStyle(Color.textSecondary)
                        Text(goal.goalType.displayName)
                            .font(.headingSmall).fontWeight(.bold)
                            .foregroundStyle(Color.textPrimary)
                    }
                }
                Spacer()
                Button { showAbandonConfirm = true } label: {
                    Image(systemName: "ellipsis")
                        .font(.bodyMedium)
                        .foregroundStyle(Color.textSecondary)
                        .frame(width: 34, height: 34)
                        .background(Color.surfaceSecondary)
                        .clipShape(Circle())
                }
            }

            Divider()

            HStack(spacing: 0) {
                GoalStatItem(label: String(localized: "goal.detail.stat.target"), value: goal.targetText)
                Spacer()
                GoalStatItem(label: String(localized: "goal.detail.stat.deadline"), value: goal.formattedTargetDate)
                Spacer()
                if let days = goal.daysRemaining {
                    GoalStatItem(
                        label: String(localized: "goal.detail.stat.daysLeft"),
                        value: String(format: String(localized: "goal.detail.stat.daysLeft.value"), days),
                        valueColor: days >= 14 ? .textPrimary : .brandDanger
                    )
                }
            }

            Divider()

            NavigationLink(destination: progressDestination()) {
                HStack(spacing: 6) {
                    Image(systemName: "chart.line.uptrend.xyaxis")
                        .font(.labelSmall)
                    Text(String(localized: "goal.detail.viewProgress"))
                        .font(.labelSmall)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.captionXSmall).fontWeight(.semibold)
                }
                .foregroundStyle(Color.brandPrimary)
            }
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.xl))
        .shadow(color: .black.opacity(0.07), radius: 10, x: 0, y: 4)
        .confirmationDialog(Text("goal.abandon.title"), isPresented: $showAbandonConfirm) {
            Button(String(localized: "goal.abandon.confirm"), role: .destructive) { onAbandon() }
            Button(String(localized: "common.cancel"), role: .cancel) {}
        } message: {
            Text(String(localized: "goal.abandon.message"))
        }
    }
}

private struct GoalStatItem: View {
    let label: String
    let value: String
    var valueColor: Color = .textPrimary

    var body: some View {
        VStack(spacing: 4) {
            Text(label)
                .font(.captionXSmall)
                .foregroundStyle(Color.textSecondary)
            Text(value)
                .font(.bodyMedium).fontWeight(.bold)
                .foregroundStyle(valueColor)
        }
    }
}

// MARK: - Empty State

private struct EmptyGoalCard: View {
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 16) {
                ZStack {
                    Circle().fill(Color.surfaceCard).frame(width: 68, height: 68)
                    Image(systemName: "plus.circle.fill")
                        .font(.system(size: 30)) // design-lint:ignore — SF Symbol or special
                        .foregroundStyle(Color.brandPrimary)
                }
                VStack(spacing: 6) {
                    Text(String(localized: "goal.empty.title"))
                        .font(.bodyLarge).fontWeight(.bold)
                        .foregroundStyle(Color.textPrimary)
                    Text(String(localized: "goal.empty.message"))
                        .font(.bodySmall)
                        .foregroundStyle(Color.textSecondary)
                        .multilineTextAlignment(.center)
                        .lineSpacing(3)
                }
                Text(String(localized: "goal.empty.action"))
                    .font(.bodyMedium).fontWeight(.semibold)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 30) // design-lint:ignore — micro/hero spacing
                    .padding(.vertical, 11) // design-lint:ignore — micro/hero spacing
                    .background(Color.brandPrimary)
                    .clipShape(Capsule())
            }
            .frame(maxWidth: .infinity)
            .padding(Spacing.xxl) // design-lint:ignore — micro/hero spacing
            .background(Color.surfaceCard)
            .clipShape(RoundedRectangle(cornerRadius: Radius.xl))
            .shadow(color: .black.opacity(0.06), radius: 10, x: 0, y: 4)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Past Goals

private struct PastGoalsSection: View {
    let goals: [GoalSummary]

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(String(localized: "goal.history.title"))
                .font(.headingMedium).fontWeight(.bold)
                .foregroundStyle(Color.textPrimary)
                .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing

            VStack(spacing: 10) {
                ForEach(goals) { goal in
                    PastGoalRow(goal: goal).padding(.horizontal, Spacing.xl)
                }
            }
        }
    }
}

private struct PastGoalRow: View {
    let goal: GoalSummary

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: goal.goalType.icon)
                .font(.bodyMedium)
                .foregroundStyle(Color.textSecondary)
                .frame(width: 36, height: 36)
                .background(Color.surfaceSecondary)
                .clipShape(RoundedRectangle(cornerRadius: Radius.sm))

            VStack(alignment: .leading, spacing: 3) {
                Text(goal.goalType.displayName)
                    .font(.bodyMedium).fontWeight(.semibold)
                    .foregroundStyle(Color.textPrimary)
                Text(goal.formattedTargetDate)
                    .font(.caption)
                    .foregroundStyle(Color.textSecondary)
            }

            Spacer()

            Text(goal.status.displayName)
                .font(.captionBold)
                .foregroundStyle(goal.status.badgeColor)
                .padding(.horizontal, Spacing.md) // design-lint:ignore — micro/hero spacing
                .padding(.vertical, Spacing.xs) // design-lint:ignore — micro/hero spacing
                .background(goal.status.badgeColor.opacity(0.12))
                .clipShape(Capsule())
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
    }
}
