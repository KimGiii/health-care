//
//  GoalProvider.swift
//  HealthCareWidget
//

import WidgetKit
import Foundation

struct GoalProvider: TimelineProvider {
    private let store = WidgetDataStore()

    func placeholder(in context: Context) -> GoalEntry {
        GoalEntry.placeholder
    }

    func getSnapshot(in context: Context, completion: @escaping (GoalEntry) -> Void) {
        let snapshot = store?.loadGoal() ?? .empty
        completion(GoalEntry(date: Date(), snapshot: snapshot))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<GoalEntry>) -> Void) {
        let now = Date()
        let snapshot = store?.loadGoal() ?? .empty
        let entry = GoalEntry(date: now, snapshot: snapshot)

        // 목표/체중은 빈번히 바뀌지 않으므로 1시간 또는 자정 중 더 이른 쪽으로 갱신.
        let nextRefresh = Self.nextRefreshDate(from: now)
        completion(Timeline(entries: [entry], policy: .after(nextRefresh)))
    }

    private static func nextRefreshDate(from now: Date) -> Date {
        let hourLater = now.addingTimeInterval(60 * 60)
        let calendar = Calendar.current
        let tomorrow = calendar.startOfDay(for: now.addingTimeInterval(24 * 60 * 60))
        return min(hourLater, tomorrow)
    }
}
