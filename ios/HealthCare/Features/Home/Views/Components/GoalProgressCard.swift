import SwiftUI

// MARK: - GoalProgressCard
//
// 활성 목표 진행 현황 카드.
// PlanSection / PlanActiveCard / PlanEmptyCard 에서 분리.

struct GoalProgressCard: View {
    let goal: GoalSummary?

    var body: some View {
        NavigationLink(destination: GoalSettingView()) {
            if let goal {
                ActiveGoalContent(goal: goal)
            } else {
                EmptyGoalContent()
            }
        }
        .buttonStyle(.plain)
        .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
    }
}

// MARK: - Active Goal

private struct ActiveGoalContent: View {
    let goal: GoalSummary

    /// 표시용 진행률(%) — 반올림. 링·라벨·접근성 라벨이 동일 값을 쓰도록 한 곳에서 계산한다.
    /// 다른 화면(GoalProgressView, GoalSettingView)도 %.0f 반올림을 사용한다.
    private var percentText: String {
        String(format: "%.0f", goal.progressRatio * 100)
    }

    var body: some View {
        HStack(alignment: .center, spacing: 16) {
            // 진행 링 (compact ProgressRing)
            ProgressRing(
                progress: goal.progressRatio,
                gradient: .ringCalorie,
                size: .standard,
                value: percentText,
                unit: "%",
                trackColor: Color.brandDusk.opacity(0.10)
            )

            VStack(alignment: .leading, spacing: 6) {
                Text("GOAL · \(goal.goalType.displayName.uppercased())")
                    .eyebrowStyle()
                HStack(spacing: 6) {
                    Image(systemName: goal.goalType.icon)
                    Text(goal.goalType.displayName)
                }
                .font(.bodyLarge).fontWeight(.bold)
                .foregroundStyle(Color.textHeadline)

                if let days = goal.daysRemaining {
                    HStack(spacing: 6) {
                        Capsule()
                            .fill(Color.textHeadline)
                            .frame(width: 48, height: 20)
                            .overlay(
                                Text("D-\(days)")
                                    .font(.system(size: 10, weight: .heavy, design: .rounded)) // design-lint:ignore — SF Symbol or hero numeric
                                    .foregroundStyle(Color.surfaceCard)
                            )
                        Text(String(format: String(localized: "home.goal.percentComplete"), percentText))
                            .font(.caption).fontWeight(.medium)
                            .foregroundStyle(Color.textSecondary)
                    }
                }
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.captionBold).fontWeight(.bold)
                .foregroundStyle(Color.textHeadline.opacity(0.30))
                .accessibilityHidden(true)
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(
            RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                .fill(Color.surfaceCard)
                .overlay(
                    RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                        .stroke(Color.cardStroke, lineWidth: 1)
                )
        )
        .elevation(.low)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            String(format: String(localized: "home.goal.a11y"),
                   goal.goalType.displayName, percentText)
            + (goal.daysRemaining.map {
                ", " + String(format: String(localized: "home.goal.daysRemaining.a11y"), $0)
            } ?? "")
        )
    }
}

// MARK: - Empty Goal

private struct EmptyGoalContent: View {
    var body: some View {
        HStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(LinearGradient.forestHero)
                    .frame(width: 52, height: 52)
                Image(systemName: "target")
                    .font(.headingLarge)
                    .foregroundStyle(Color.brandAccentGlow)
            }
            .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text("목표가 아직 없어요")
                    .font(.headingSmall)
                    .foregroundStyle(Color.textHeadline)
                Text("목표를 세우고 기록을 시작해 보세요")
                    .font(.caption)
                    .foregroundStyle(Color.textSecondary)
            }
            Spacer()
            Image(systemName: "plus.circle.fill")
                .font(.system(size: 24)) // design-lint:ignore — SF Symbol or hero numeric
                .foregroundStyle(Color.brandAccent)
                .accessibilityHidden(true)
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(
            RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                .fill(Color.surfaceCard)
                .overlay(
                    RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                        .stroke(Color.cardStroke, lineWidth: 1)
                )
        )
        .elevation(.low)
    }
}

// MARK: - Preview

#Preview {
    ZStack {
        Color.brandBone.ignoresSafeArea()
        VStack(spacing: 16) {
            GoalProgressCard(goal: GoalSummary(
                goalId: 1, goalType: .WEIGHT_LOSS,
                targetValue: 70, targetUnit: "kg",
                targetDate: "2026-12-31", startDate: "2026-01-01",
                status: .ACTIVE, percentComplete: 62
            ))
            GoalProgressCard(goal: nil)
        }
        .padding()
    }
}
