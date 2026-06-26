//
//  GoalWidgetView.swift
//  HealthCareWidget
//
//  Small: 활성 목표 + 달성률 링
//  Medium: 목표 + 최근 7일 체중 미니 라인 차트 (Swift Charts)
//

import SwiftUI
import WidgetKit
import Charts

// 색은 Shared/WidgetColors.swift 에서 정의 (다크/라이트 동적).
// 차트 색은 accent와 동일.
private extension Color {
    static var widgetChart: Color { .widgetAccent }
}

// MARK: - Dispatch

struct GoalWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: GoalEntry

    var body: some View {
        switch family {
        case .systemSmall:
            GoalSmallView(snapshot: entry.snapshot)
        case .systemMedium:
            GoalMediumView(snapshot: entry.snapshot)
        default:
            GoalSmallView(snapshot: entry.snapshot)
        }
    }
}

// MARK: - Small

private struct GoalSmallView: View {
    let snapshot: GoalWidgetSnapshot

    var body: some View {
        Group {
            if let goal = snapshot.goal {
                content(goal: goal)
            } else {
                emptyState
            }
        }
        .padding(12)
        .widgetURL(WidgetConstants.DeepLink.goal)
    }

    @ViewBuilder
    private func content(goal: GoalWidgetSnapshot.ActiveGoal) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top, spacing: 2) {
                Image(systemName: goal.systemImage)
                    .font(.system(size: 11, weight: .semibold))
                Text(goal.title)
                    .font(.system(size: 11, weight: .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.55)
                    .allowsTightening(true)
                    .layoutPriority(1)
                Spacer(minLength: 2)
                if let days = goal.daysRemaining {
                    dDayBadge(days: days)
                }
            }
            .foregroundStyle(Color.widgetBrand)

            Spacer(minLength: 0)

            ZStack {
                GoalRing(progress: goal.progress)
                    .frame(width: 82, height: 82)

                VStack(spacing: 0) {
                    Text("\(Int((goal.progress * 100).rounded()))%")
                        .font(.system(size: 22, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.widgetBrand)
                    Text(goal.targetText)
                        .font(.system(size: 9, weight: .medium))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity)

            Spacer(minLength: 0)
        }
    }


    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 6) {
            Image(systemName: "target")
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(Color.widgetBrand)
            Spacer(minLength: 0)
            Text("활성 목표 없음")
                .font(.system(size: 13, weight: .semibold))
            Text("앱에서 새 목표를 설정해 보세요.")
                .font(.system(size: 11))
                .foregroundStyle(.secondary)
                .lineLimit(2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Medium

private struct GoalMediumView: View {
    let snapshot: GoalWidgetSnapshot

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            leftColumn
            rightColumn
        }
        .padding(14)
        .widgetURL(WidgetConstants.DeepLink.goal)
    }

    @ViewBuilder
    private var leftColumn: some View {
        if let goal = snapshot.goal {
            VStack(alignment: .leading, spacing: 16) {
                // 타이틀 — 배지 없이 한 줄 전체 사용
                HStack(spacing: 4) {
                    Image(systemName: goal.systemImage)
                        .font(.system(size: 11, weight: .semibold))
                    Text(goal.title)
                        .font(.system(size: 11, weight: .semibold))
                        .lineLimit(1)
                }
                .foregroundStyle(Color.widgetBrand)

                // 링 + 퍼센트 — 위 여백으로 헤더와 분리, 위젯 중앙에 시각 무게 둠
                ZStack {
                    GoalRing(progress: goal.progress)
                        .frame(width: 90, height: 90)
                    VStack(spacing: 1) {
                        Text("\(Int((goal.progress * 100).rounded()))%")
                            .font(.system(size: 22, weight: .bold, design: .rounded))
                            .foregroundStyle(Color.widgetBrand)
                        Text(goal.targetText)
                            .font(.system(size: 9, weight: .medium))
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                        // D-day 배지를 링 아래로 이동 — 타이틀 공간 확보
                        if let days = goal.daysRemaining {
                            dDayBadge(days: days)
                                .padding(.top, 2)
                        }
                    }
                }

                Spacer(minLength: 0)
            }
            .frame(width: 130, alignment: .leading)
        } else {
            VStack(alignment: .leading, spacing: 6) {
                Image(systemName: "target")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(Color.widgetBrand)
                Text("활성 목표 없음")
                    .font(.system(size: 12, weight: .semibold))
            }
            .frame(width: 130, alignment: .leading)
        }
    }

    @ViewBuilder
    private var rightColumn: some View {
        VStack(alignment: .leading, spacing: 8) {
            switch snapshot.metricPoints.count {
            case 0:
                emptyState
            default:
                metricHeader
                sparkline
                metricFooter
            }
        }
        .padding(.top, 14)  // 헤더와 차트를 좌측 링 중심에 맞춰 아래로 이동
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: header — 현재 목표 지표 큰 글씨 + 변화량

    @ViewBuilder
    private var metricHeader: some View {
        if let latest = snapshot.metricPoints.last {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(String(format: "%.1f", latest.value))
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .foregroundStyle(Color.widgetBrand)
                    .minimumScaleFactor(0.7)
                    .lineLimit(1)
                Text(snapshot.metricKind.unit)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.secondary)
                Spacer(minLength: 0)
                if let delta = snapshot.metricDelta, delta != 0 {
                    deltaPill(delta)
                }
            }
        }
    }

    private func deltaPill(_ value: Double) -> some View {
        let down = value < 0
        let sign = value > 0 ? "+" : ""
        let isImproving = down == snapshot.metricKind.prefersLowerValue
        let tint: Color = isImproving ? .widgetAccent : .widgetBrand
        return HStack(spacing: 2) {
            Image(systemName: down ? "arrow.down" : "arrow.up")
                .font(.system(size: 9, weight: .bold))
            Text("\(sign)\(String(format: "%.1f", value))")
                .font(.system(size: 10, weight: .bold, design: .rounded))
        }
        .foregroundStyle(tint)
        .padding(.horizontal, 6)
        .padding(.vertical, 2)
        .background(Capsule().fill(tint.opacity(0.18)))
    }

    // MARK: sparkline

    @ViewBuilder
    private var sparkline: some View {
        if snapshot.metricPoints.count >= 2 {
            MetricSparkline(points: snapshot.metricPoints)
                .frame(height: 36)
        } else {
            // 1개일 때는 빈 자리만 — 헤더 큰 숫자로 정보 전달 충분.
            Color.clear.frame(height: 36)
        }
    }

    // MARK: footer — 기간 라벨

    @ViewBuilder
    private var metricFooter: some View {
        HStack {
            if let first = snapshot.metricPoints.first {
                Text(shortDate(first.date))
                    .font(.system(size: 9, weight: .medium))
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Text(snapshot.metricKind.label)
                .font(.system(size: 9, weight: .medium))
                .foregroundStyle(.secondary)
            Spacer()
            if snapshot.metricPoints.count >= 2,
               let last = snapshot.metricPoints.last {
                Text(shortDate(last.date))
                    .font(.system(size: 9, weight: .medium))
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func shortDate(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateFormat = "M.d"
        return f.string(from: date)
    }

    // MARK: empty

    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 4) {
            Image(systemName: snapshot.goal?.systemImage ?? "chart.line.uptrend.xyaxis")
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(Color.widgetAccent)
            Text(snapshot.metricKind.emptyText)
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(.secondary)
                .lineLimit(2)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }

}

// MARK: - D-day Badge (Small/Medium 공통)

@ViewBuilder
private func dDayBadge(days: Int) -> some View {
    Text("D-\(days)")
        .font(.system(size: 10, weight: .bold, design: .rounded))
        .foregroundStyle(Color.widgetBrand)
        .lineLimit(1)
        .fixedSize(horizontal: true, vertical: false)  // 헤더 좁을 때 wrap 방지
        .padding(.horizontal, 6)
        .padding(.vertical, 2)
        .background(Capsule().fill(Color.widgetBrand.opacity(0.14)))
}

// MARK: - Ring

private struct GoalRing: View {
    let progress: Double

    var body: some View {
        let clamped = max(0, min(progress, 1))
        ZStack {
            Circle()
                .stroke(Color.widgetTrack, lineWidth: 8)
            Circle()
                .trim(from: 0, to: clamped)
                .stroke(
                    Color.widgetAccent,
                    style: StrokeStyle(lineWidth: 8, lineCap: .round)
                )
                .rotationEffect(.degrees(-90))
        }
    }
}

// MARK: - Chart

private struct MetricSparkline: View {
    let points: [GoalWidgetSnapshot.MetricPoint]

    var body: some View {
        // 변화량 강조 — y 범위를 데이터 폭에 딱 맞게 잡되, 최소 폭을 보장해
        // 작은 변화에 차트가 과장되지 않도록 함.
        let values = points.map(\.value)
        let dataMin = values.min() ?? 0
        let dataMax = values.max() ?? 1
        let span = max(dataMax - dataMin, 1.0)
        let padding = span * 0.25
        let minValue = dataMin - padding
        let maxValue = dataMax + padding

        let last = points.last

        Chart {
            ForEach(points, id: \.date) { point in
                LineMark(
                    x: .value("Date", point.date),
                    y: .value("Value", point.value)
                )
                .interpolationMethod(.monotone)
                .foregroundStyle(Color.widgetChart)
                .lineStyle(StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))

                AreaMark(
                    x: .value("Date", point.date),
                    yStart: .value("Min", minValue),
                    yEnd: .value("Value", point.value)
                )
                .interpolationMethod(.monotone)
                .foregroundStyle(
                    LinearGradient(
                        colors: [Color.widgetChart.opacity(0.22), Color.widgetChart.opacity(0.0)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )

                // 각 측정점에 작은 점 — 사용자가 며칠 잰지 한눈에 인식.
                PointMark(
                    x: .value("Date", point.date),
                    y: .value("Value", point.value)
                )
                .symbol { Circle().fill(Color.widgetChart).frame(width: 3, height: 3) }
            }

            // 마지막 측정만 살짝 더 큰 점 + 흰 테두리로 강조.
            if let last {
                PointMark(
                    x: .value("Date", last.date),
                    y: .value("Value", last.value)
                )
                .symbol {
                    ZStack {
                        // 시스템 배경색으로 outline → 라이트(흰)/다크(검정 가까운 회색) 모두 자연스러움.
                        Circle().fill(Color(uiColor: .systemBackground)).frame(width: 9, height: 9)
                        Circle().fill(Color.widgetChart).frame(width: 6, height: 6)
                    }
                }
            }
        }
        .chartYScale(domain: minValue...maxValue)
        .chartXAxis(.hidden)
        .chartYAxis(.hidden)
        .chartPlotStyle { plot in
            plot.background(Color.clear)
        }
    }
}

// MARK: - Preview

#if DEBUG
@available(iOSApplicationExtension 17.0, *)
#Preview("Goal Small", as: .systemSmall) {
    GoalWidget()
} timeline: {
    GoalEntry.placeholder
    GoalEntry(date: Date(), snapshot: .empty)
}

@available(iOSApplicationExtension 17.0, *)
#Preview("Goal Medium", as: .systemMedium) {
    GoalWidget()
} timeline: {
    GoalEntry.placeholder
}
#endif
