//
//  StreakProvider.swift
//  HealthCareWidget
//

import WidgetKit
import Foundation

struct StreakProvider: TimelineProvider {
    private let store = WidgetDataStore()

    func placeholder(in context: Context) -> StreakEntry {
        StreakEntry.placeholder
    }

    func getSnapshot(in context: Context, completion: @escaping (StreakEntry) -> Void) {
        let snapshot = store?.loadStreak() ?? .empty
        completion(StreakEntry(date: Date(), snapshot: snapshot))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<StreakEntry>) -> Void) {
        let now = Date()
        let snapshot = store?.loadStreak() ?? .empty
        let entry = StreakEntry(date: now, snapshot: snapshot)

        // 자정 경계가 가장 중요 — streak가 깨질 가능성이 있는 시점.
        // 그 외에는 1시간 간격으로 충분.
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
