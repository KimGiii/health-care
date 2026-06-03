import SwiftUI

// MARK: - StreakCard
//
// 연속 기록일 수를 표시하는 소형 카드.
// 최근 7일 점(dot) 캘린더를 함께 보여준다.

struct StreakCard: View {
    let streakDays: Int
    /// 최근 7일 활동 배열 (오래된 날부터, count == 7)
    let weeklyActivity: [DayActivity]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // 헤더
            Text("STREAK")
                .eyebrowStyle()

            // 큰 숫자
            HStack(alignment: .lastTextBaseline, spacing: 4) {
                Text("\(streakDays)")
                    .font(.system(size: 40, weight: .heavy, design: .rounded)) // design-lint:ignore — SF Symbol or hero numeric
                    .foregroundStyle(streakDays > 0 ? Color.textHeadline : Color.textTertiary)
                    .contentTransition(.numericText())
                Text("home.streak.days")
                    .font(.labelSmall)
                    .foregroundStyle(Color.textSecondary)
                    .padding(.bottom, Spacing.xs) // design-lint:ignore — micro/hero spacing
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(String(format: String(localized: "home.streak.a11y"), streakDays))

            // 7일 도트 캘린더
            HStack(spacing: 6) {
                ForEach(Array(weeklyActivity.enumerated()), id: \.offset) { idx, day in
                    DotDay(
                        isActive: day.caloriesIn > 0 || day.caloriesBurned > 0,
                        isToday: idx == weeklyActivity.count - 1
                    )
                }
            }
            .accessibilityHidden(true)
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .frame(maxWidth: .infinity, alignment: .leading)
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

// MARK: - DotDay

private struct DotDay: View {
    let isActive: Bool
    let isToday: Bool

    var body: some View {
        Circle()
            .fill(isActive ? Color.brandAccent : Color.brandDusk.opacity(0.10))
            .frame(width: 9, height: 9)
            .overlay(
                Circle()
                    .stroke(isToday ? Color.brandPrimary : Color.clear, lineWidth: 1.5)
                    .padding(-3) // design-lint:ignore — micro/hero spacing
            )
    }
}

#Preview {
    ZStack {
        Color.brandBone.ignoresSafeArea()

        let sampleActivity = (0..<7).map { i in
            DayActivity(date: "2026-05-0\(i+1)", caloriesIn: i < 5 ? 1500 : 0, caloriesBurned: i < 5 ? 300 : 0, durationMinutes: i < 5 ? 40 : 0)
        }

        StreakCard(streakDays: 5, weeklyActivity: sampleActivity)
            .padding(Spacing.xl) // design-lint:ignore — micro/hero spacing
    }
}
