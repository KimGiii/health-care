//
//  StreakEntry.swift
//  HealthCareWidget
//

import WidgetKit
import Foundation

struct StreakEntry: TimelineEntry {
    let date: Date
    let snapshot: StreakWidgetSnapshot

    static let placeholder = StreakEntry(date: Date(), snapshot: .placeholder)
}
