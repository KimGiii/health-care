import Foundation

@MainActor
protocol GoalWidgetSnapshotPublishing {
    func publish(goal: GoalSummary?)
    func publish(
        goal: GoalSummary?,
        recentMeasurements: [MeasurementResponse],
        recentSessions: [SessionSummary]
    )
    func publish(recentMeasurements: [MeasurementResponse])
}

struct AppGoalWidgetSnapshotPublisher: GoalWidgetSnapshotPublishing {
    func publish(goal: GoalSummary?) {
        guard let store = WidgetDataStore() else { return }

        let previous = store.loadGoal()
        let snapshot = GoalWidgetSnapshotFactory.make(
            goal: goal,
            previousSnapshot: previous
        )
        store.saveGoal(snapshot)
        WidgetReloadCenter.reloadGoal()
    }

    func publish(
        goal: GoalSummary?,
        recentMeasurements: [MeasurementResponse],
        recentSessions: [SessionSummary]
    ) {
        guard let store = WidgetDataStore() else { return }

        let snapshot = GoalWidgetSnapshotFactory.make(
            goal: goal,
            recentMeasurements: recentMeasurements,
            recentSessions: recentSessions,
            previousSnapshot: store.loadGoal()
        )
        store.saveGoal(snapshot)
        WidgetReloadCenter.reloadGoal()
    }

    func publish(recentMeasurements: [MeasurementResponse]) {
        guard let store = WidgetDataStore() else { return }

        let previous = store.loadGoal()
        let snapshot = GoalWidgetSnapshotFactory.make(
            recentMeasurements: recentMeasurements,
            previousSnapshot: previous
        )
        store.saveGoal(snapshot)
        WidgetReloadCenter.reloadGoal()
    }
}

enum GoalWidgetSnapshotFactory {
    static func make(goal: GoalSummary?, previousSnapshot: GoalWidgetSnapshot?) -> GoalWidgetSnapshot {
        GoalWidgetSnapshot(
            goal: goal.map(GoalWidgetSnapshot.ActiveGoal.init(goal:)),
            metricKind: goal.map(metricKind(for:)) ?? previousSnapshot?.metricKind ?? .weight,
            metricPoints: previousSnapshot?.metricPoints ?? []
        )
    }

    static func make(
        goal: GoalSummary?,
        recentMeasurements: [MeasurementResponse],
        recentSessions: [SessionSummary],
        previousSnapshot: GoalWidgetSnapshot?
    ) -> GoalWidgetSnapshot {
        let metricKind = goal.map(metricKind(for:)) ?? previousSnapshot?.metricKind ?? .weight
        let points = metricPoints(
            for: metricKind,
            measurements: recentMeasurements,
            sessions: recentSessions,
            previousSnapshot: previousSnapshot
        )

        return GoalWidgetSnapshot(
            goal: goal.map(GoalWidgetSnapshot.ActiveGoal.init(goal:)),
            metricKind: metricKind,
            metricPoints: points
        )
    }

    static func make(
        recentMeasurements: [MeasurementResponse],
        previousSnapshot: GoalWidgetSnapshot?
    ) -> GoalWidgetSnapshot {
        let metricKind = previousSnapshot?.metricKind ?? .weight
        let points = metricPoints(
            for: metricKind,
            measurements: recentMeasurements,
            sessions: [],
            previousSnapshot: previousSnapshot
        )

        return GoalWidgetSnapshot(
            goal: previousSnapshot?.goal,
            metricKind: metricKind,
            metricPoints: points
        )
    }

    static func metricKind(for goal: GoalSummary) -> GoalWidgetSnapshot.MetricKind {
        switch goal.goalType {
        case .WEIGHT_LOSS, .GENERAL_HEALTH:
            return .weight
        case .MUSCLE_GAIN:
            return .muscleMass
        case .BODY_RECOMPOSITION:
            return .bodyFat
        case .ENDURANCE:
            return .enduranceMinutes
        }
    }

    private static func metricPoints(
        for kind: GoalWidgetSnapshot.MetricKind,
        measurements: [MeasurementResponse],
        sessions: [SessionSummary],
        previousSnapshot: GoalWidgetSnapshot?
    ) -> [GoalWidgetSnapshot.MetricPoint] {
        switch kind {
        case .weight:
            return measurements.metricPoints { $0.weightKg }
        case .muscleMass:
            return measurements.metricPoints { $0.muscleMassKg }
        case .bodyFat:
            return measurements.metricPoints { $0.bodyFatPct }
        case .enduranceMinutes:
            let points = sessions.enduranceMinutePoints
            return points.isEmpty ? previousSnapshot?.metricPoints ?? [] : points
        }
    }
}

private extension GoalWidgetSnapshot.ActiveGoal {
    init(goal: GoalSummary) {
        self.init(
            goalId: goal.goalId,
            title: goal.goalType.displayName,
            systemImage: goal.goalType.icon,
            targetText: goal.targetText,
            progress: goal.progressRatio,
            daysRemaining: goal.daysRemaining
        )
    }
}

private extension Array where Element == MeasurementResponse {
    func metricPoints(_ value: (MeasurementResponse) -> Double?) -> [GoalWidgetSnapshot.MetricPoint] {
        compactMap { measurement -> GoalWidgetSnapshot.WeightPoint? in
            guard let date = measurement.parsedDate,
                  let value = value(measurement) else { return nil }
            return GoalWidgetSnapshot.WeightPoint(date: date, weightKg: value)
        }
        .sorted { $0.date < $1.date }
        .suffix(7)
        .map { GoalWidgetSnapshot.MetricPoint(date: $0.date, value: $0.weightKg) }
    }
}

private extension Array where Element == SessionSummary {
    var enduranceMinutePoints: [GoalWidgetSnapshot.MetricPoint] {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "en_US_POSIX")

        let totalsByDate = reduce(into: [String: Int]()) { result, session in
            result[session.sessionDate, default: 0] += session.durationMinutes ?? 0
        }

        return totalsByDate
            .compactMap { dateString, minutes -> GoalWidgetSnapshot.MetricPoint? in
                guard minutes > 0,
                      let date = formatter.date(from: dateString) else { return nil }
                return GoalWidgetSnapshot.MetricPoint(date: date, value: Double(minutes))
            }
            .sorted { $0.date < $1.date }
            .suffix(7)
            .map { $0 }
    }
}
