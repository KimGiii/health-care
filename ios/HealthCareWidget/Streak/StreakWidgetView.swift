//
//  StreakWidgetView.swift
//  HealthCareWidget
//

import SwiftUI
import WidgetKit

// 색은 Shared/WidgetColors.swift 에서 동적 정의 (다크/라이트).

// MARK: - Dispatch

struct StreakWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: StreakEntry

    var body: some View {
        switch family {
        case .systemSmall:
            StreakSmallView(snapshot: entry.snapshot)
        case .accessoryCircular:
            StreakAccessoryCircularView(snapshot: entry.snapshot)
        case .accessoryRectangular:
            StreakAccessoryRectangularView(snapshot: entry.snapshot)
        default:
            StreakSmallView(snapshot: entry.snapshot)
        }
    }
}

// MARK: - Small (홈 화면)

private struct StreakSmallView: View {
    let snapshot: StreakWidgetSnapshot

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 4) {
                Image(systemName: "flame.fill")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color.widgetFlame)
                Text("기록 스트릭")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(.secondary)
            }

            Spacer(minLength: 0)

            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(snapshot.displayText)
                    .font(.system(size: 44, weight: .bold, design: .rounded))
                    .foregroundStyle(Color.widgetBrand)
                    .minimumScaleFactor(0.5)
                    .lineLimit(1)
                Text("일")
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                    .foregroundStyle(.secondary)
            }

            Spacer(minLength: 0)

            todayChecks
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .widgetURL(WidgetConstants.DeepLink.streak)
    }

    private var todayChecks: some View {
        HStack(spacing: 6) {
            checkChip(label: "식단", isDone: snapshot.didLogDietToday)
            checkChip(label: "운동", isDone: snapshot.didLogExerciseToday)
        }
    }

    private func checkChip(label: String, isDone: Bool) -> some View {
        HStack(spacing: 3) {
            Image(systemName: isDone ? "checkmark.circle.fill" : "circle")
                .font(.system(size: 9, weight: .bold))
                .foregroundStyle(isDone ? Color.widgetAccent : Color.widgetSecondaryText)
            Text(label)
                .font(.system(size: 9, weight: .semibold))
                .foregroundStyle(isDone ? Color.widgetBrand : Color.widgetSecondaryText)
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 3)
        .background(
            Capsule().fill((isDone ? Color.widgetAccent : Color.widgetSecondaryText).opacity(isDone ? 0.18 : 0.12))
        )
    }
}

// MARK: - Accessory Circular (잠금화면 원형)

private struct StreakAccessoryCircularView: View {
    let snapshot: StreakWidgetSnapshot

    var body: some View {
        ZStack {
            AccessoryWidgetBackground()
            VStack(spacing: 0) {
                Image(systemName: "flame.fill")
                    .font(.system(size: 10, weight: .bold))
                Text(snapshot.displayText)
                    .font(.system(size: 18, weight: .bold, design: .rounded))
                    .minimumScaleFactor(0.5)
                    .lineLimit(1)
            }
        }
        .widgetURL(WidgetConstants.DeepLink.streak)
    }
}

// MARK: - Accessory Rectangular (잠금화면 직사각형)

private struct StreakAccessoryRectangularView: View {
    let snapshot: StreakWidgetSnapshot

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "flame.fill")
                .font(.system(size: 22, weight: .bold))
                .widgetAccentable()

            VStack(alignment: .leading, spacing: 1) {
                Text("\(snapshot.displayText)일 연속")
                    .font(.system(size: 15, weight: .bold, design: .rounded))
                Text(subtitle)
                    .font(.system(size: 10, weight: .medium))
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .widgetURL(WidgetConstants.DeepLink.streak)
    }

    private var subtitle: String {
        switch (snapshot.didLogDietToday, snapshot.didLogExerciseToday) {
        case (true, true):   return "오늘 식단·운동 ✓"
        case (true, false):  return "오늘 운동 남음"
        case (false, true):  return "오늘 식단 남음"
        case (false, false): return "오늘 기록을 시작하세요"
        }
    }
}

// MARK: - Preview

#if DEBUG
#Preview("Streak Small", as: .systemSmall) {
    StreakWidget()
} timeline: {
    StreakEntry.placeholder
    StreakEntry(date: Date(), snapshot: .empty)
}

#Preview("Streak Circular", as: .accessoryCircular) {
    StreakWidget()
} timeline: {
    StreakEntry.placeholder
}

#Preview("Streak Rectangular", as: .accessoryRectangular) {
    StreakWidget()
} timeline: {
    StreakEntry.placeholder
}
#endif
