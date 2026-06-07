//
//  WidgetReloadCenter.swift
//  HealthCare
//
//  WidgetCenter 호출을 한 곳으로 모은 래퍼.
//  - 앱 타겟에서만 사용 (위젯 익스텐션 내부에서는 호출 금지).
//  - 메인 액터에서 호출하는 것을 권장.
//

import Foundation
#if canImport(WidgetKit)
import WidgetKit
#endif

public enum WidgetReloadCenter {
    /// 모든 위젯 갱신.
    public static func reloadAll() {
        #if canImport(WidgetKit)
        WidgetCenter.shared.reloadAllTimelines()
        #endif
    }

    /// 칼로리 위젯만 갱신 (식단 기록 직후 호출).
    public static func reloadCalorie() {
        #if canImport(WidgetKit)
        WidgetCenter.shared.reloadTimelines(ofKind: WidgetConstants.Kind.calorie)
        #endif
    }

    /// 목표 위젯만 갱신.
    public static func reloadGoal() {
        #if canImport(WidgetKit)
        WidgetCenter.shared.reloadTimelines(ofKind: WidgetConstants.Kind.goal)
        #endif
    }

    /// 스트릭 위젯만 갱신.
    public static func reloadStreak() {
        #if canImport(WidgetKit)
        WidgetCenter.shared.reloadTimelines(ofKind: WidgetConstants.Kind.streak)
        #endif
    }
}
