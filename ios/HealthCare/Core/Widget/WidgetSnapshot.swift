//
//  WidgetSnapshot.swift
//  HealthCare
//
//  앱이 위젯에 전달하는 스냅샷 데이터 모델.
//  Codable로 JSON 직렬화 후 App Group UserDefaults에 저장한다.
//

import Foundation

// MARK: - Calorie

public struct CalorieWidgetSnapshot: Codable, Equatable, Sendable {
    public let date: Date
    public let consumedKcal: Int
    public let targetKcal: Int
    public let proteinG: Double
    public let proteinTargetG: Double
    public let carbsG: Double
    public let carbsTargetG: Double
    public let fatG: Double
    public let fatTargetG: Double
    public let updatedAt: Date

    public init(
        date: Date,
        consumedKcal: Int,
        targetKcal: Int,
        proteinG: Double,
        proteinTargetG: Double,
        carbsG: Double,
        carbsTargetG: Double,
        fatG: Double,
        fatTargetG: Double,
        updatedAt: Date = Date()
    ) {
        self.date = date
        self.consumedKcal = consumedKcal
        self.targetKcal = targetKcal
        self.proteinG = proteinG
        self.proteinTargetG = proteinTargetG
        self.carbsG = carbsG
        self.carbsTargetG = carbsTargetG
        self.fatG = fatG
        self.fatTargetG = fatTargetG
        self.updatedAt = updatedAt
    }

    /// 0.0 ~ 1.0 (1.0 초과 가능 → 뷰에서 clamp)
    public var calorieProgress: Double {
        guard targetKcal > 0 else { return 0 }
        return Double(consumedKcal) / Double(targetKcal)
    }

    public var proteinProgress: Double {
        guard proteinTargetG > 0 else { return 0 }
        return proteinG / proteinTargetG
    }

    public var carbsProgress: Double {
        guard carbsTargetG > 0 else { return 0 }
        return carbsG / carbsTargetG
    }

    public var fatProgress: Double {
        guard fatTargetG > 0 else { return 0 }
        return fatG / fatTargetG
    }

    public var remainingKcal: Int {
        max(targetKcal - consumedKcal, 0)
    }
}

public extension CalorieWidgetSnapshot {
    /// 위젯 placeholder / 첫 사용 시 표시할 더미 데이터.
    static let placeholder = CalorieWidgetSnapshot(
        date: Date(),
        consumedKcal: 1240,
        targetKcal: 2000,
        proteinG: 78,
        proteinTargetG: 130,
        carbsG: 140,
        carbsTargetG: 220,
        fatG: 42,
        fatTargetG: 65
    )

    /// 데이터 없음 상태.
    static let empty = CalorieWidgetSnapshot(
        date: Date(),
        consumedKcal: 0,
        targetKcal: 2000,
        proteinG: 0,
        proteinTargetG: 130,
        carbsG: 0,
        carbsTargetG: 220,
        fatG: 0,
        fatTargetG: 65
    )
}
