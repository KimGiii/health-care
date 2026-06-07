//
//  CalorieWidget.swift
//  HealthCareWidget
//
//  StaticConfiguration 기반 칼로리 위젯 — Small / Medium 패밀리 지원.
//

import WidgetKit
import SwiftUI

struct CalorieWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(
            kind: WidgetConstants.Kind.calorie,
            provider: CalorieProvider()
        ) { entry in
            CalorieWidgetView(entry: entry)
                .containerBackground(.background, for: .widget)
        }
        .configurationDisplayName("오늘의 칼로리")
        .description("오늘 섭취 칼로리와 영양 균형을 한눈에 확인하세요.")
        .supportedFamilies([.systemSmall, .systemMedium])
        .contentMarginsDisabled()
    }
}
