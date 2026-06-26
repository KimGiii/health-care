//
//  CalorieProvider.swift
//  HealthCareWidget
//
//  App Group UserDefaults에서 CalorieWidgetSnapshot 을 읽어 TimelineEntry 로 변환.
//  네트워크 호출 없음 — 앱이 저장한 스냅샷만 사용한다.
//

import WidgetKit
import Foundation

struct CalorieProvider: TimelineProvider {
    private let store = WidgetDataStore()

    // MARK: - Placeholder (시스템이 위젯 추가 미리보기로 사용)

    func placeholder(in context: Context) -> CalorieEntry {
        CalorieEntry.placeholder
    }

    // MARK: - Snapshot (위젯 갤러리 / 동적 미리보기)

    func getSnapshot(in context: Context, completion: @escaping (CalorieEntry) -> Void) {
        let snapshot = store?.loadCalorie() ?? .empty
        completion(CalorieEntry(date: Date(), snapshot: snapshot))
    }

    // MARK: - Timeline (실제 표시)

    func getTimeline(in context: Context, completion: @escaping (Timeline<CalorieEntry>) -> Void) {
        let now = Date()
        let snapshot = store?.loadCalorie() ?? .empty

        let entry = CalorieEntry(date: now, snapshot: snapshot)

        // 갱신 정책:
        // 1. 자정 직전까지 30분 간격으로 자동 갱신
        // 2. 자정 경계에서 한 번 더 갱신 (오늘 vs 어제 구분)
        // 앱이 기록 직후 WidgetCenter.reloadTimelines(...) 를 호출하는 게 메인 트리거.
        let nextRefresh = Self.nextRefreshDate(from: now)
        let timeline = Timeline(entries: [entry], policy: .after(nextRefresh))
        completion(timeline)
    }

    /// 다음 자동 갱신 시각: 30분 후 또는 자정 중 더 이른 쪽.
    private static func nextRefreshDate(from now: Date) -> Date {
        let halfHourLater = now.addingTimeInterval(30 * 60)

        let calendar = Calendar.current
        let tomorrow = calendar.startOfDay(for: now.addingTimeInterval(24 * 60 * 60))

        return min(halfHourLater, tomorrow)
    }
}
