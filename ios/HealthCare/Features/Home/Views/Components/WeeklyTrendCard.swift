import SwiftUI
import Charts

// MARK: - WeeklyTrendCard
//
// 최근 7일 칼로리 섭취 추세를 바 차트로 보여주는 카드.
// Swift Charts (iOS 16+) 사용.

struct WeeklyTrendCard: View {
    let weeklyActivity: [DayActivity]

    @State private var selectedDay: DayActivity? = nil

    private var maxCalories: Double {
        max(weeklyActivity.map(\.caloriesIn).max() ?? 0, 500)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            // 헤더
            HStack {
                Text("WEEKLY")
                    .eyebrowStyle()
                Spacer()
                if let sel = selectedDay {
                    HStack(spacing: 10) {
                        // 섭취
                        HStack(alignment: .lastTextBaseline, spacing: 2) {
                            Image(systemName: "fork.knife")
                                .font(.system(size: 9)) // design-lint:ignore — SF Symbol icon sizing
                                .foregroundStyle(Color.brandAccent)
                            Text(String(format: "%.0f", sel.caloriesIn))
                                .font(.system(size: 14, weight: .heavy, design: .rounded)) // design-lint:ignore — hero numeric
                                .foregroundStyle(Color.brandAccent)
                            Text("kcal")
                                .font(.captionXSmall)
                                .foregroundStyle(Color.textSecondary)
                        }
                        // 소모
                        if sel.caloriesBurned > 0 {
                            HStack(alignment: .lastTextBaseline, spacing: 2) {
                                Image(systemName: "flame.fill")
                                    .font(.system(size: 9)) // design-lint:ignore — SF Symbol icon sizing
                                    .foregroundStyle(Color.brandEmber)
                                Text(String(format: "%.0f", sel.caloriesBurned))
                                    .font(.system(size: 14, weight: .heavy, design: .rounded)) // design-lint:ignore — hero numeric
                                    .foregroundStyle(Color.brandEmber)
                                Text("kcal")
                                    .font(.captionXSmall)
                                    .foregroundStyle(Color.textSecondary)
                            }
                        }
                    }
                    .transition(.opacity)
                } else {
                    Text("7일 섭취 추세")
                        .font(.captionXSmall)
                        .foregroundStyle(Color.textTertiary)
                }
            }

            // 바 차트
            Chart(weeklyActivity, id: \.date) { day in
                BarMark(
                    x: .value("날짜", shortLabel(day.date)),
                    y: .value("칼로리", day.caloriesIn)
                )
                .foregroundStyle(
                    day.date == selectedDay?.date
                        ? Color.brandPrimary
                        : Color.brandAccent.opacity(day.caloriesIn > 0 ? 0.75 : 0.18)
                )
                .cornerRadius(4)
            }
            .chartYScale(domain: 0...maxCalories * 1.2)
            .chartXAxis {
                AxisMarks(values: .automatic) { _ in
                    AxisValueLabel()
                        .font(.captionXSmall)
                        .foregroundStyle(Color.textTertiary)
                }
            }
            .chartYAxis(.hidden)
            .frame(height: 90)
            .chartOverlay { proxy in
                GeometryReader { geo in
                    Rectangle()
                        .fill(Color.clear)
                        .contentShape(Rectangle())
                        .gesture(
                            DragGesture(minimumDistance: 0)
                                .onChanged { val in
                                    // plot area origin을 빼야 정확한 x 좌표 매핑
                                    let origin = geo[proxy.plotAreaFrame].origin
                                    let xInPlot = val.location.x - origin.x
                                    guard let date: String = proxy.value(atX: xInPlot) else { return }
                                    selectedDay = weeklyActivity.first { shortLabel($0.date) == date }
                                }
                                .onEnded { _ in
                                    withAnimation(.easeOut(duration: 0.3)) { selectedDay = nil }
                                }
                        )
                }
            }

            // 운동 소모 요약 줄
            let totalBurned = weeklyActivity.map(\.caloriesBurned).reduce(0, +)
            if totalBurned > 0 {
                HStack(spacing: 6) {
                    Image(systemName: "flame.fill")
                        .font(.captionXSmall).fontWeight(.bold)
                        .foregroundStyle(Color.brandEmber)
                    Text(String(format: String(localized: "home.weekly.burned.summary"), Int(totalBurned)))
                        .font(.captionXSmall)
                        .foregroundStyle(Color.textSecondary)
                }
            }
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
        .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
    }

    // MARK: - Helpers

    private func shortLabel(_ dateStr: String) -> String {
        let parts = dateStr.split(separator: "-")
        guard parts.count == 3, let day = Int(parts[2]) else { return dateStr }
        let weekdays = [
            String(localized: "common.weekday.sun"),
            String(localized: "common.weekday.mon"),
            String(localized: "common.weekday.tue"),
            String(localized: "common.weekday.wed"),
            String(localized: "common.weekday.thu"),
            String(localized: "common.weekday.fri"),
            String(localized: "common.weekday.sat"),
        ]
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        if let date = formatter.date(from: dateStr) {
            let idx = Calendar.current.component(.weekday, from: date) - 1
            return weekdays[idx]
        }
        return "\(day)"
    }
}

#Preview {
    ZStack {
        Color.brandBone.ignoresSafeArea()
        let activities = [
            DayActivity(date: "2026-05-02", caloriesIn: 1850, caloriesBurned: 280, durationMinutes: 40),
            DayActivity(date: "2026-05-03", caloriesIn: 1600, caloriesBurned: 0,   durationMinutes: 0),
            DayActivity(date: "2026-05-04", caloriesIn: 1920, caloriesBurned: 350, durationMinutes: 50),
            DayActivity(date: "2026-05-05", caloriesIn: 1400, caloriesBurned: 200, durationMinutes: 30),
            DayActivity(date: "2026-05-06", caloriesIn: 2100, caloriesBurned: 420, durationMinutes: 60),
            DayActivity(date: "2026-05-07", caloriesIn: 1750, caloriesBurned: 310, durationMinutes: 45),
            DayActivity(date: "2026-05-08", caloriesIn: 900,  caloriesBurned: 320, durationMinutes: 42),
        ]
        WeeklyTrendCard(weeklyActivity: activities)
            .padding(.vertical, Spacing.xxxl) // design-lint:ignore — micro/hero spacing
    }
}
