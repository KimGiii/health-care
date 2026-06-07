//
//  WidgetConstants.swift
//  HealthCare
//
//  앱과 위젯 익스텐션 양쪽에서 공유하는 상수.
//  반드시 양쪽 타겟(Target Membership)에 모두 포함시켜야 한다.
//

import Foundation

public enum WidgetConstants {
    /// App Group ID — Apple Developer Portal과 두 타겟의 Entitlements에 등록되어 있어야 함.
    public static let appGroupID = "group.com.kingloo.gainsy.widgets"

    /// URL 스킴 (Info.plist CFBundleURLSchemes에 등록됨).
    public static let urlScheme = "gainsy"

    /// 위젯 종류별 식별자 (`WidgetCenter.reloadTimelines(ofKind:)`와 일치해야 함).
    public enum Kind {
        public static let calorie = "CalorieWidget"
        public static let goal = "GoalWidget"
        public static let streak = "StreakWidget"
    }

    /// UserDefaults 저장 키.
    public enum StorageKey {
        public static let calorieSnapshot = "widget.calorie.snapshot"
        public static let goalSnapshot = "widget.goal.snapshot"
        public static let streakSnapshot = "widget.streak.snapshot"
    }

    /// 위젯 딥링크 URL.
    public enum DeepLink {
        public static let calorie = URL(string: "\(WidgetConstants.urlScheme)://widget/calorie")!
        public static let goal = URL(string: "\(WidgetConstants.urlScheme)://widget/goal")!
        public static let streak = URL(string: "\(WidgetConstants.urlScheme)://widget/streak")!
    }
}
