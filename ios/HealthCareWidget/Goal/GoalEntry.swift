//
//  GoalEntry.swift
//  HealthCareWidget
//

import WidgetKit
import Foundation

struct GoalEntry: TimelineEntry {
    let date: Date
    let snapshot: GoalWidgetSnapshot

    static let placeholder = GoalEntry(date: Date(), snapshot: .placeholder)
}
