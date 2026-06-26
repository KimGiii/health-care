//
//  CalorieEntry.swift
//  HealthCareWidget
//
//  칼로리 위젯의 TimelineEntry.
//  App Group으로부터 읽은 CalorieWidgetSnapshot을 감싸는 단순 래퍼.
//

import WidgetKit
import Foundation

struct CalorieEntry: TimelineEntry {
    let date: Date
    let snapshot: CalorieWidgetSnapshot

    /// 캐시가 없거나 첫 사용 시 표시할 entry.
    static let placeholder = CalorieEntry(
        date: Date(),
        snapshot: .placeholder
    )
}
