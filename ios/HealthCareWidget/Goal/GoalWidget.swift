//
//  GoalWidget.swift
//  HealthCareWidget
//

import WidgetKit
import SwiftUI

struct GoalWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(
            kind: WidgetConstants.Kind.goal,
            provider: GoalProvider()
        ) { entry in
            GoalWidgetView(entry: entry)
                .containerBackground(.background, for: .widget)
        }
        .configurationDisplayName("목표 진행률")
        .description("활성 목표 달성률과 최근 체중 변화를 확인하세요.")
        .supportedFamilies([.systemSmall, .systemMedium])
        .contentMarginsDisabled()
    }
}
