//
//  CalorieWidgetView.swift
//  HealthCareWidget
//
//  Small / Medium 두 패밀리 지원.
//  외부 디자인시스템 의존 없이 위젯 내부에서 색을 직접 정의.
//

import SwiftUI
import WidgetKit

// MARK: - Entry View (family dispatch)
// 색은 Shared/WidgetColors.swift 에서 다크/라이트 동적으로 정의됨.

struct CalorieWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: CalorieEntry

    var body: some View {
        switch family {
        case .systemSmall:
            CalorieSmallView(snapshot: entry.snapshot)
        case .systemMedium:
            CalorieMediumView(snapshot: entry.snapshot)
        default:
            CalorieSmallView(snapshot: entry.snapshot)
        }
    }
}

// MARK: - Small

private struct CalorieSmallView: View {
    let snapshot: CalorieWidgetSnapshot

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 4) {
                Image(systemName: "flame.fill")
                    .font(.system(size: 11, weight: .semibold))
                Text("오늘 칼로리")
                    .font(.system(size: 11, weight: .semibold))
            }
            .foregroundStyle(.secondary)

            Spacer(minLength: 0)

            ZStack {
                CalorieRing(progress: snapshot.calorieProgress)
                    .frame(width: 86, height: 86)

                VStack(spacing: 0) {
                    Text("\(snapshot.consumedKcal)")
                        .font(.system(size: 22, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.widgetBrand)
                        .minimumScaleFactor(0.6)
                    Text("/ \(snapshot.targetKcal)")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity)

            Spacer(minLength: 0)

            Text(remainingText)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .padding(12)
        .widgetURL(WidgetConstants.DeepLink.calorie)
    }

    private var remainingText: String {
        if snapshot.consumedKcal >= snapshot.targetKcal {
            return "목표 달성 🎉"
        }
        return "남은 \(snapshot.remainingKcal) kcal"
    }
}

// MARK: - Medium

private struct CalorieMediumView: View {
    let snapshot: CalorieWidgetSnapshot

    var body: some View {
        HStack(spacing: 16) {
            // Left: ring
            ZStack {
                CalorieRing(progress: snapshot.calorieProgress)
                    .frame(width: 96, height: 96)

                VStack(spacing: 0) {
                    Text("\(snapshot.consumedKcal)")
                        .font(.system(size: 22, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.widgetBrand)
                        .minimumScaleFactor(0.6)
                    Text("kcal")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundStyle(.secondary)
                }
            }

            // Right: macros
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 4) {
                    Image(systemName: "flame.fill")
                        .font(.system(size: 11, weight: .semibold))
                    Text("오늘의 영양")
                        .font(.system(size: 12, weight: .semibold))
                }
                .foregroundStyle(.secondary)

                MacroBar(
                    label: "단백질",
                    current: snapshot.proteinG,
                    target: snapshot.proteinTargetG,
                    progress: snapshot.proteinProgress,
                    tint: .widgetProtein
                )
                MacroBar(
                    label: "탄수화물",
                    current: snapshot.carbsG,
                    target: snapshot.carbsTargetG,
                    progress: snapshot.carbsProgress,
                    tint: .widgetCarbs
                )
                MacroBar(
                    label: "지방",
                    current: snapshot.fatG,
                    target: snapshot.fatTargetG,
                    progress: snapshot.fatProgress,
                    tint: .widgetFat
                )
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(14)
        .widgetURL(WidgetConstants.DeepLink.calorie)
    }
}

// MARK: - Ring

private struct CalorieRing: View {
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

// MARK: - Macro Bar

private struct MacroBar: View {
    let label: String
    let current: Double
    let target: Double
    let progress: Double
    let tint: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 4) {
                Text(label)
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(.secondary)
                    .fixedSize()
                Spacer(minLength: 0)
                Text("\(Int(current))/\(Int(target))g")
                    .font(.system(size: 9, weight: .medium, design: .rounded))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .fixedSize()
            }

            // GeometryReader 대신 scaleEffect — 위젯에서 레이아웃 안전
            Capsule()
                .fill(tint.opacity(0.22))
                .frame(height: 6)
                .overlay(alignment: .leading) {
                    Capsule()
                        .fill(tint)
                        .frame(height: 6)
                        .scaleEffect(x: max(0, min(progress, 1)), anchor: .leading)
                }
        }
    }
}

// MARK: - Preview

#if DEBUG
#Preview("Small", as: .systemSmall) {
    CalorieWidget()
} timeline: {
    CalorieEntry.placeholder
    CalorieEntry(date: Date(), snapshot: .empty)
}

#Preview("Medium", as: .systemMedium) {
    CalorieWidget()
} timeline: {
    CalorieEntry.placeholder
}
#endif
