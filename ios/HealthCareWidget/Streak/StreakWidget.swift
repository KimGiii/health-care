//
//  StreakWidget.swift
//  HealthCareWidget
//
//  systemSmall (홈) + accessoryCircular/Rectangular (잠금화면) 지원.
//

import WidgetKit
import SwiftUI

struct StreakWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(
            kind: WidgetConstants.Kind.streak,
            provider: StreakProvider()
        ) { entry in
            StreakWidgetView(entry: entry)
                .containerBackground(.background, for: .widget)
        }
        .configurationDisplayName("기록 스트릭")
        .description("연속 기록 일수를 한눈에 확인하세요.")
        .supportedFamilies([
            .systemSmall,
            .accessoryCircular,
            .accessoryRectangular
        ])
        .contentMarginsDisabled()
    }
}
