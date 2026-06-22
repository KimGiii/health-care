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

// MARK: - Goal

public struct GoalWidgetSnapshot: Codable, Equatable, Sendable {
    /// 활성 목표가 없으면 nil — 위젯이 "활성 목표 없음" 상태로 표시.
    public let goal: ActiveGoal?
    /// 활성 목표 타입에 맞는 최근 지표 포인트 — 오름차순 정렬 권장.
    public let metricKind: MetricKind
    public let metricPoints: [MetricPoint]
    public let updatedAt: Date

    private enum CodingKeys: String, CodingKey {
        case goal, metricKind, metricPoints, recentWeights, updatedAt
    }

    public struct ActiveGoal: Codable, Equatable, Sendable {
        /// 백엔드 목표 ID — 위젯 탭 라우팅 시 GoalProgressView(goalId:)에 직접 사용.
        public let goalId: Int
        /// 사용자에게 보일 짧은 라벨 (예: "체중 감량", "근육 증가")
        public let title: String
        /// SF Symbol 이름 (예: "arrow.down.circle.fill")
        public let systemImage: String
        /// 목표값 + 단위 표시용 (예: "65 kg")
        public let targetText: String
        /// 0.0 ~ 1.0
        public let progress: Double
        /// 남은 일수 — nil이면 표시 생략
        public let daysRemaining: Int?

        public init(goalId: Int, title: String, systemImage: String, targetText: String, progress: Double, daysRemaining: Int?) {
            self.goalId = goalId
            self.title = title
            self.systemImage = systemImage
            self.targetText = targetText
            self.progress = max(0, min(progress, 1))
            self.daysRemaining = daysRemaining
        }
    }

    public struct WeightPoint: Codable, Equatable, Sendable {
        public let date: Date
        public let weightKg: Double

        public init(date: Date, weightKg: Double) {
            self.date = date
            self.weightKg = weightKg
        }
    }

    public enum MetricKind: String, Codable, Equatable, Sendable {
        case weight
        case muscleMass
        case bodyFat
        case enduranceMinutes

        public var label: String {
            switch self {
            case .weight: return "체중"
            case .muscleMass: return "골격근량"
            case .bodyFat: return "체지방률"
            case .enduranceMinutes: return "운동 시간"
            }
        }

        public var unit: String {
            switch self {
            case .weight, .muscleMass: return "kg"
            case .bodyFat: return "%"
            case .enduranceMinutes: return "분"
            }
        }

        public var emptyText: String {
            switch self {
            case .weight: return "체중 기록을 시작하세요"
            case .muscleMass: return "골격근량 기록이 필요해요"
            case .bodyFat: return "체지방률 기록이 필요해요"
            case .enduranceMinutes: return "운동 기록을 시작하세요"
            }
        }

        public var prefersLowerValue: Bool {
            switch self {
            case .weight, .bodyFat: return true
            case .muscleMass, .enduranceMinutes: return false
            }
        }
    }

    public struct MetricPoint: Codable, Equatable, Sendable {
        public let date: Date
        public let value: Double

        public init(date: Date, value: Double) {
            self.date = date
            self.value = value
        }
    }

    public init(
        goal: ActiveGoal?,
        metricKind: MetricKind,
        metricPoints: [MetricPoint],
        updatedAt: Date = Date()
    ) {
        self.goal = goal
        self.metricKind = metricKind
        self.metricPoints = metricPoints
        self.updatedAt = updatedAt
    }

    public init(goal: ActiveGoal?, recentWeights: [WeightPoint], updatedAt: Date = Date()) {
        self.init(
            goal: goal,
            metricKind: .weight,
            metricPoints: recentWeights.map { MetricPoint(date: $0.date, value: $0.weightKg) },
            updatedAt: updatedAt
        )
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        goal = try container.decodeIfPresent(ActiveGoal.self, forKey: .goal)
        updatedAt = try container.decodeIfPresent(Date.self, forKey: .updatedAt) ?? Date()

        if let metricKind = try container.decodeIfPresent(MetricKind.self, forKey: .metricKind),
           let metricPoints = try container.decodeIfPresent([MetricPoint].self, forKey: .metricPoints) {
            self.metricKind = metricKind
            self.metricPoints = metricPoints
        } else {
            let oldWeights = try container.decodeIfPresent([WeightPoint].self, forKey: .recentWeights) ?? []
            (metricKind, metricPoints) = GoalWidgetSnapshot.migrate(from: oldWeights)
        }
    }

    /// 구버전 `recentWeights` 포맷을 현행 `(MetricKind, [MetricPoint])` 쌍으로 변환한다.
    /// 데이터 소스가 recentWeights 하나이므로 항상 `.weight` 를 반환한다.
    public static func migrate(from legacy: [WeightPoint]) -> (MetricKind, [MetricPoint]) {
        let points = legacy.map { MetricPoint(date: $0.date, value: $0.weightKg) }
        return (.weight, points)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(goal, forKey: .goal)
        try container.encode(metricKind, forKey: .metricKind)
        try container.encode(metricPoints, forKey: .metricPoints)
        try container.encode(updatedAt, forKey: .updatedAt)
    }

    /// 가장 오래된 → 가장 최근 지표 변화량. 데이터 부족 시 nil.
    public var metricDelta: Double? {
        guard let first = metricPoints.first?.value,
              let last = metricPoints.last?.value,
              metricPoints.count >= 2 else { return nil }
        return last - first
    }
}

// MARK: - Streak

public struct StreakWidgetSnapshot: Codable, Equatable, Sendable {
    /// 오늘 기준 연속 기록 일수. 끊긴 경우 0.
    public let streakDays: Int
    /// 현재 streak 계산이 도달 가능한 최대값 (예: 백엔드/클라이언트 윈도우 한계).
    /// 표시 정밀도용 — `streakDays`가 이 값과 같으면 "N+"로 표시.
    public let cap: Int
    /// 오늘 식단 기록이 1개 이상 있는지.
    public let didLogDietToday: Bool
    /// 오늘 운동 기록이 1개 이상 있는지.
    public let didLogExerciseToday: Bool
    public let updatedAt: Date

    public init(
        streakDays: Int,
        cap: Int,
        didLogDietToday: Bool,
        didLogExerciseToday: Bool,
        updatedAt: Date = Date()
    ) {
        self.streakDays = max(0, streakDays)
        self.cap = cap
        self.didLogDietToday = didLogDietToday
        self.didLogExerciseToday = didLogExerciseToday
        self.updatedAt = updatedAt
    }

    /// "5" 또는 cap 도달 시 "7+" 같은 라벨.
    public var displayText: String {
        streakDays >= cap ? "\(cap)+" : "\(streakDays)"
    }

    /// 오늘 둘 다 기록했는지 — UI에서 완료 강조용.
    public var isTodayComplete: Bool {
        didLogDietToday && didLogExerciseToday
    }
}

public extension StreakWidgetSnapshot {
    static let placeholder = StreakWidgetSnapshot(
        streakDays: 5,
        cap: 7,
        didLogDietToday: true,
        didLogExerciseToday: false
    )

    static let empty = StreakWidgetSnapshot(
        streakDays: 0,
        cap: 7,
        didLogDietToday: false,
        didLogExerciseToday: false
    )
}

public extension GoalWidgetSnapshot {
    /// 위젯 placeholder (미리보기 / 첫 사용).
    static let placeholder = GoalWidgetSnapshot(
        goal: ActiveGoal(
            goalId: 0,
            title: "체중 감량",
            systemImage: "arrow.down.circle.fill",
            targetText: "65 kg",
            progress: 0.62,
            daysRemaining: 24
        ),
        metricKind: .weight,
        metricPoints: (0..<7).map { i in
            let cal = Calendar.current
            let date = cal.date(byAdding: .day, value: -(6 - i), to: Date()) ?? Date()
            return MetricPoint(date: date, value: 70.4 - Double(i) * 0.18)
        }
    )

    /// 활성 목표 없음 상태.
    static let empty = GoalWidgetSnapshot(goal: nil, metricKind: .weight, metricPoints: [])
}
