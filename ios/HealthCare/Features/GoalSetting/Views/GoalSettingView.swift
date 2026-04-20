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
                                .padding(.top, 40)
                        } else if let active = viewModel.activeGoal {
                            ActiveGoalCard(goal: active) {
                                Task {
                                    await viewModel.abandonGoal(
                                        id: active.id,
                                        apiClient: container.apiClient
                                    )
                                }
                            }
                            .padding(.horizontal, 20)
                        } else {
                            EmptyGoalCard { viewModel.showAddGoal = true }
                                .padding(.horizontal, 20)
                        }

                        if !viewModel.pastGoals.isEmpty {
                            PastGoalsSection(goals: viewModel.pastGoals)
                        }
                    }
                    .padding(.vertical, 24)
                    .padding(.bottom, 80)
                }
            }
            .ignoresSafeArea(edges: .top)
            .background(Color.surfaceGrouped)
            .refreshable { await viewModel.load(apiClient: container.apiClient) }

            // + FAB
            Button { viewModel.showAddGoal = true } label: {
                Image(systemName: "plus")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 58, height: 58)
                    .background(Color.brandPrimary)
                    .clipShape(Circle())
                    .shadow(color: Color.brandPrimary.opacity(0.45), radius: 12, x: 0, y: 6)
            }
            .padding(.trailing, 24)
            .padding(.bottom, 32)
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $viewModel.showAddGoal) {
            AddGoalView { _ in
                Task { await viewModel.goalCreated(apiClient: container.apiClient) }
            }
        }
        .alert("오류", isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("확인", role: .cancel) {}
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
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(.white)
                            .padding(10)
                            .background(.white.opacity(0.15))
                            .clipShape(Circle())
                    }
                    Spacer()
                    Text("나의 목표")
                        .font(.system(size: 18, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                    Spacer()
                    Color.clear.frame(width: 40, height: 40)
                }
                .padding(.horizontal, 20)
                .padding(.top, 56)

                if let goal = activeGoal {
                    GoalProgressRing(goal: goal).padding(.top, 16)
                } else {
                    NoGoalPlaceholder().padding(.top, 16)
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
                    .fill(Color.surfaceGrouped)
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
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(.white)
                    Text("달성")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundStyle(.white.opacity(0.7))
                }
            }

            VStack(alignment: .leading, spacing: 9) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(goal.goalType.displayName)
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(.white)
                    Text("목표: \(goal.targetText)")
                        .font(.system(size: 13))
                        .foregroundStyle(.white.opacity(0.8))
                }
                HStack(spacing: 5) {
                    Image(systemName: "calendar")
                        .font(.system(size: 11))
                    Text(goal.formattedTargetDate)
                        .font(.system(size: 12, weight: .medium))
                }
                .foregroundStyle(.white.opacity(0.8))

                if let days = goal.daysRemaining {
                    DaysRemainingBadge(days: days)
                }
            }
        }
        .padding(.horizontal, 28)
    }
}

private struct DaysRemainingBadge: View {
    let days: Int
    private var color: Color { days >= 14 ? .brandAccent : (days >= 0 ? .brandWarning : .brandDanger) }

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "clock.fill").font(.system(size: 10))
            Text("D-\(days)").font(.system(size: 11, weight: .semibold))
        }
        .foregroundStyle(color)
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .background(Color.white.opacity(0.15))
        .clipShape(Capsule())
    }
}

private struct NoGoalPlaceholder: View {
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "target")
                .font(.system(size: 34))
                .foregroundStyle(.white.opacity(0.45))
            Text("설정된 목표가 없습니다")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(.white.opacity(0.65))
        }
        .frame(height: 110)
    }
}

// MARK: - Active Goal Card

private struct ActiveGoalCard: View {
    let goal: GoalSummary
    let onAbandon: () -> Void
    @State private var showAbandonConfirm = false

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                HStack(spacing: 10) {
                    Image(systemName: goal.goalType.icon)
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(Color.brandPrimary)
                        .frame(width: 40, height: 40)
                        .background(Color.brandSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("진행 중인 목표")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(Color.textSecondary)
                        Text(goal.goalType.displayName)
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(Color.textPrimary)
                    }
                }
                Spacer()
                Button { showAbandonConfirm = true } label: {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 15))
                        .foregroundStyle(Color.textSecondary)
                        .frame(width: 34, height: 34)
                        .background(Color.surfaceSecondary)
                        .clipShape(Circle())
                }
            }

            Divider()

            HStack(spacing: 0) {
                GoalStatItem(label: "목표", value: goal.targetText)
                Spacer()
                GoalStatItem(label: "마감일", value: goal.formattedTargetDate)
                Spacer()
                if let days = goal.daysRemaining {
                    GoalStatItem(
                        label: "남은 일수",
                        value: "\(days)일",
                        valueColor: days >= 14 ? .textPrimary : .brandDanger
                    )
                }
            }
        }
        .padding(18)
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .shadow(color: .black.opacity(0.07), radius: 10, x: 0, y: 4)
        .confirmationDialog("목표 포기", isPresented: $showAbandonConfirm) {
            Button("목표 포기", role: .destructive) { onAbandon() }
            Button("취소", role: .cancel) {}
        } message: {
            Text("현재 목표를 포기하시겠습니까?\n히스토리에는 계속 기록됩니다.")
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
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Color.textSecondary)
            Text(value)
                .font(.system(size: 14, weight: .bold))
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
                    Circle().fill(Color.brandSurface).frame(width: 68, height: 68)
                    Image(systemName: "plus.circle.fill")
                        .font(.system(size: 30))
                        .foregroundStyle(Color.brandPrimary)
                }
                VStack(spacing: 6) {
                    Text("목표를 설정해보세요")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Color.textPrimary)
                    Text("체중 감량, 근육 증가 등 나만의 목표를\n설정하고 달성률을 추적하세요.")
                        .font(.system(size: 13))
                        .foregroundStyle(Color.textSecondary)
                        .multilineTextAlignment(.center)
                        .lineSpacing(3)
                }
                Text("목표 설정하기")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 30)
                    .padding(.vertical, 11)
                    .background(Color.brandPrimary)
                    .clipShape(Capsule())
            }
            .frame(maxWidth: .infinity)
            .padding(28)
            .background(Color.surfacePrimary)
            .clipShape(RoundedRectangle(cornerRadius: 20))
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
            Text("목표 히스토리")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Color.textPrimary)
                .padding(.horizontal, 20)

            VStack(spacing: 10) {
                ForEach(goals) { goal in
                    PastGoalRow(goal: goal).padding(.horizontal, 20)
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
                .font(.system(size: 15))
                .foregroundStyle(Color.textSecondary)
                .frame(width: 36, height: 36)
                .background(Color.surfaceSecondary)
                .clipShape(RoundedRectangle(cornerRadius: 8))

            VStack(alignment: .leading, spacing: 3) {
                Text(goal.goalType.displayName)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color.textPrimary)
                Text(goal.formattedTargetDate)
                    .font(.system(size: 12))
                    .foregroundStyle(Color.textSecondary)
            }

            Spacer()

            Text(goal.status.displayName)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(goal.status.badgeColor)
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
                .background(goal.status.badgeColor.opacity(0.12))
                .clipShape(Capsule())
        }
        .padding(14)
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}
